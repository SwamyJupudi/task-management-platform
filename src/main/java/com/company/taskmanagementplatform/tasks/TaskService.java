package com.company.taskmanagementplatform.tasks;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectContext;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.tasks.dto.UpdateTaskRequest;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * Tasks: raising them, editing them, assigning them, and moving them through their statuses.
 *
 * <p>Dependencies live in {@link TaskDependencyService} and subtasks in their own module, following
 * the shape of the permissions and of the requirements' module list.
 *
 * <p>Reaching other modules happens through their services and never their tables: {@link
 * ProjectAccessFacade} answers everything about the project a task sits in, and {@link
 * MembershipService} whether somebody belongs to the workspace.
 *
 * <p>Every method takes identifiers and loads the row itself, because the guard authorizes in its own
 * read-only transaction and anything it returned would arrive detached.
 *
 * <p>Anything that could move a project's completion re-derives it before returning. That is the one
 * cross-module write in the platform, and it is a call into {@code projects} rather than an update
 * here, because {@code projects} owns the column.
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final TaskNumberAllocator numbers;
    private final TaskLabelService labels;
    private final TaskDependencyRepository dependencies;
    private final TaskMapper mapper;
    private final ProjectAccessFacade projects;
    private final MembershipService workspaceMembers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    TaskService(
            TaskRepository tasks,
            TaskNumberAllocator numbers,
            TaskLabelService labels,
            TaskDependencyRepository dependencies,
            TaskMapper mapper,
            ProjectAccessFacade projects,
            MembershipService workspaceMembers,
            ApplicationEventPublisher events,
            Clock clock) {
        this.tasks = tasks;
        this.numbers = numbers;
        this.labels = labels;
        this.dependencies = dependencies;
        this.mapper = mapper;
        this.projects = projects;
        this.workspaceMembers = workspaceMembers;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Raises a task in TODO, numbered against its project.
     *
     * <p>The opening status is not the caller's to choose. Allowing it would make the transition
     * rules optional, since any state could be reached by creating a task already in it.
     *
     * <p>The number is allocated inside this transaction, so a creation that fails takes the number
     * back with it rather than leaving a gap.
     */
    @Transactional
    public TaskResponse create(
            UUID workspaceId, ProjectContext project, CreateTaskRequest request, UUID creatorUserId) {

        String title = requireTitle(request.title());
        requireDateOrder(request.startDate(), request.dueDate());

        int number = numbers.nextFor(project.projectId(), workspaceId);
        Task task = Task.create(workspaceId, project.projectId(), number, title, creatorUserId);

        task.describe(trimToNull(request.description()));
        task.schedule(request.startDate(), request.dueDate());
        task.estimate(requireNonNegative(request.estimatedMinutes(), "Estimated effort"));
        task.record(requireNonNegative(request.actualMinutes(), "Actual effort"));

        if (request.priority() != null) {
            task.reprioritise(parsePriority(request.priority()));
        }

        if (request.assigneeUserId() != null) {
            requireProjectMember(project.projectId(), request.assigneeUserId());
            task.assignTo(request.assigneeUserId());
        }

        task.reportedBy(resolveReporter(workspaceId, request.reporterUserId(), creatorUserId));

        tasks.save(task);
        tasks.flush();

        labels.replace(workspaceId, task.getId(), request.labels());

        events.publishEvent(new TaskEvents.TaskCreated(
                workspaceId,
                project.projectId(),
                task.getId(),
                task.getTaskNumber(),
                task.getTitle(),
                task.getAssigneeUserId(),
                creatorUserId));

        projects.recalculateProgress(workspaceId, project.projectId());

        return mapper.toResponse(task, creatorUserId);
    }

    @Transactional
    public TaskResponse update(UUID workspaceId, UUID taskId, UpdateTaskRequest request, UUID actorUserId) {
        Task task = requireTask(workspaceId, taskId);

        if (request.title() != null) {
            task.retitle(requireTitle(request.title()));
        }
        if (request.description() != null) {
            task.describe(trimToNull(request.description()));
        }
        if (request.priority() != null) {
            task.reprioritise(parsePriority(request.priority()));
        }
        if (request.estimatedMinutes() != null) {
            task.estimate(requireNonNegative(request.estimatedMinutes(), "Estimated effort"));
        }
        if (request.actualMinutes() != null) {
            task.record(requireNonNegative(request.actualMinutes(), "Actual effort"));
        }
        if (request.boardPosition() != null) {
            task.positionAt(request.boardPosition());
        }

        if (Boolean.TRUE.equals(request.clearDates())) {
            task.schedule(null, null);
        } else if (request.startDate() != null || request.dueDate() != null) {
            // Read both dates from the request where sent, so a change to one is still
            // checked against the other rather than against a value being replaced.
            LocalDate start = request.startDate() != null ? request.startDate() : task.getStartDate();
            LocalDate due = request.dueDate() != null ? request.dueDate() : task.getDueDate();
            requireDateOrder(start, due);
            task.schedule(start, due);
        }

        if (request.labels() != null) {
            labels.replace(workspaceId, taskId, request.labels());
        }

        events.publishEvent(new TaskEvents.TaskUpdated(workspaceId, task.getProjectId(), taskId, actorUserId));
        return mapper.toResponse(task, actorUserId);
    }

    /**
     * Moves a task to another status, if the state machine allows it.
     *
     * @throws ConflictException naming both statuses, if the move is not legal from here
     */
    @Transactional
    public TaskResponse changeStatus(UUID workspaceId, UUID taskId, String target, UUID actorUserId) {
        Task task = requireTask(workspaceId, taskId);
        TaskStatus to = parseStatus(target);
        TaskStatus from = task.getStatus();

        if (from == to) {
            throw new ConflictException("That task is already " + readable(to) + ".");
        }
        if (!from.canMoveTo(to)) {
            throw new ConflictException(
                    "A task that is " + readable(from) + " cannot be moved to " + readable(to) + ".");
        }

        task.moveTo(to, clock.instant());

        events.publishEvent(
                new TaskEvents.TaskStatusChanged(workspaceId, task.getProjectId(), taskId, from, to, actorUserId));

        projects.recalculateProgress(workspaceId, task.getProjectId());

        return mapper.toResponse(task, actorUserId);
    }

    /**
     * Gives a task to somebody who is already on its project.
     *
     * <p>The membership rule is a foreign key as well as a check here. The check exists so the caller
     * reads a sentence they can act on rather than a constraint violation.
     */
    @Transactional
    public TaskResponse assign(UUID workspaceId, UUID taskId, UUID assigneeUserId, UUID actorUserId) {
        Task task = requireTask(workspaceId, taskId);
        requireProjectMember(task.getProjectId(), assigneeUserId);

        UUID previous = task.getAssigneeUserId();
        if (assigneeUserId.equals(previous)) {
            throw new ConflictException("That task is already assigned to that person.");
        }

        task.assignTo(assigneeUserId);
        events.publishEvent(new TaskEvents.TaskAssigned(
                workspaceId, task.getProjectId(), taskId, previous, assigneeUserId, actorUserId));

        return mapper.toResponse(task, actorUserId);
    }

    /** Leaves a task with nobody holding it, which is an ordinary state rather than an error. */
    @Transactional
    public TaskResponse unassign(UUID workspaceId, UUID taskId, UUID actorUserId) {
        Task task = requireTask(workspaceId, taskId);

        UUID previous = task.getAssigneeUserId();
        if (previous == null) {
            throw new ConflictException("That task is not assigned to anybody.");
        }

        task.clearAssignee();
        events.publishEvent(
                new TaskEvents.TaskAssigned(workspaceId, task.getProjectId(), taskId, previous, null, actorUserId));

        return mapper.toResponse(task, actorUserId);
    }

    /**
     * Hides a task.
     *
     * <p>Its labels stay in place, so restoring the row would be a complete restore. Its dependency
     * rows do not: they are a join table, never soft deleted, and leaving them would mean a task
     * nobody can see went on blocking one they can.
     *
     * <p>The number is not released. A link to {@code PROJ-12} must not later resolve to a different
     * task, so the project's counter keeps moving forward and its numbering shows a gap.
     */
    @Transactional
    public void delete(UUID workspaceId, UUID taskId, UUID actorUserId) {
        Task task = requireTask(workspaceId, taskId);
        UUID projectId = task.getProjectId();

        dependencies.deleteAllByTaskIdOrDependsOnTaskId(taskId, taskId);
        task.softDelete(clock.instant());

        // Published before the progress is re-derived, so the subtasks module has
        // already stood its rows down by the time anything counts them.
        events.publishEvent(new TaskEvents.TaskDeleted(workspaceId, projectId, taskId, actorUserId));

        projects.recalculateProgress(workspaceId, projectId);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(
            UUID workspaceId, TaskFilter filter, ProjectScope scope, Pageable pageable, UUID viewerUserId) {

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), TaskQuery.validateSort(pageable.getSort()));

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Page<Task> page = tasks.findAll(TaskQuery.matching(workspaceId, filter, scope, today), sorted);
        List<TaskResponse> mapped = mapper.toResponses(page.getContent(), viewerUserId);

        return new PageImpl<>(mapped, sorted, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TaskResponse describe(UUID workspaceId, UUID taskId, UUID viewerUserId) {
        return mapper.toResponse(requireTask(workspaceId, taskId), viewerUserId);
    }

    // --- rules ------------------------------------------------------------

    /**
     * Who raised the task.
     *
     * <p>Falls back to the caller, which is what "reporter" means everywhere. It stays null when the
     * caller is a platform administrator, because they hold no membership row in any workspace and
     * the column is a foreign key into that table. A task with no reporter is an ordinary state, so
     * refusing the creation would be worse than recording the truth.
     */
    private UUID resolveReporter(UUID workspaceId, UUID requested, UUID creatorUserId) {
        if (requested != null) {
            if (!workspaceMembers.isMember(workspaceId, requested)) {
                throw new BadRequestException("That person is not a member of this workspace.");
            }
            return requested;
        }
        return workspaceMembers.isMember(workspaceId, creatorUserId) ? creatorUserId : null;
    }

    private void requireProjectMember(UUID projectId, UUID userId) {
        if (!projects.isProjectMember(projectId, userId)) {
            throw new BadRequestException("That person is not a member of this task's project.");
        }
    }

    private Task requireTask(UUID workspaceId, UUID taskId) {
        return tasks.findByIdAndWorkspaceIdAndDeletedAtIsNull(taskId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", taskId));
    }

    private static void requireDateOrder(LocalDate start, LocalDate due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw new BadRequestException("A task cannot be due before it starts.");
        }
    }

    private static Integer requireNonNegative(Integer minutes, String what) {
        if (minutes != null && minutes < 0) {
            throw new BadRequestException(what + " cannot be negative.");
        }
        return minutes;
    }

    private static String requireTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty()) {
            throw new BadRequestException("A task needs a title.");
        }
        if (title.length() > 200) {
            throw new BadRequestException("A task title may be at most 200 characters.");
        }
        return title;
    }

    static TaskStatus parseStatus(String raw) {
        try {
            return TaskStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("That is not a task status.");
        }
    }

    static TaskPriority parsePriority(String raw) {
        try {
            return TaskPriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("That is not a task priority.");
        }
    }

    private static String readable(TaskStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
