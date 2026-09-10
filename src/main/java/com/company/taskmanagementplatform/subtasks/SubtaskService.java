package com.company.taskmanagementplatform.subtasks;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.subtasks.dto.CreateSubtaskRequest;
import com.company.taskmanagementplatform.subtasks.dto.SubtaskResponse;
import com.company.taskmanagementplatform.subtasks.dto.UpdateSubtaskRequest;
import com.company.taskmanagementplatform.tasks.TaskRef;
import com.company.taskmanagementplatform.tasks.TaskStatus;

/**
 * The checklist under a task: adding items, editing them, and ticking them off.
 *
 * <p>Every method takes a {@link TaskRef} rather than a task identifier, because the parent has
 * already been authorized by the time anything here runs and the reference carries the project and
 * workspace this row has to be written against. Authorization is entirely the parent's: there is no
 * subtask permission family, since a subtask is part of a task rather than a thing of its own to
 * hold rights over.
 *
 * <p>Anything that could move the parent project's completion re-derives it before returning.
 * Subtasks are the requirements' own unit of partial completion, so a checklist half ticked off is
 * what makes a project read something other than a whole number of finished tasks.
 */
@Service
public class SubtaskService {

    private final SubtaskRepository subtasks;
    private final SubtaskMapper mapper;
    private final ProjectAccessFacade projects;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SubtaskService(
            SubtaskRepository subtasks,
            SubtaskMapper mapper,
            ProjectAccessFacade projects,
            ApplicationEventPublisher events,
            Clock clock) {
        this.subtasks = subtasks;
        this.mapper = mapper;
        this.projects = projects;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SubtaskResponse> list(TaskRef task) {
        return mapper.toResponses(
                subtasks.findAllByTaskIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(task.taskId()));
    }

    /** Adds an item, at the end of the checklist unless a position says otherwise. */
    @Transactional
    public SubtaskResponse create(TaskRef task, CreateSubtaskRequest request, UUID actorUserId) {
        String title = requireTitle(request.title());
        int position = request.position() != null
                ? request.position()
                : subtasks.countByTaskIdAndDeletedAtIsNull(task.taskId());

        Subtask subtask = Subtask.create(
                task.workspaceId(), task.projectId(), task.taskId(), title, position, actorUserId);

        if (request.assigneeUserId() != null) {
            requireProjectMember(task.projectId(), request.assigneeUserId());
            subtask.assignTo(request.assigneeUserId());
        }
        subtask.dueOn(request.dueDate());

        subtasks.save(subtask);
        subtasks.flush();

        events.publishEvent(new SubtaskEvents.SubtaskCreated(
                task.workspaceId(),
                task.projectId(),
                task.taskId(),
                subtask.getId(),
                subtask.getTitle(),
                subtask.getAssigneeUserId(),
                actorUserId));

        // A new, unfinished item lowers the parent task's share of its own checklist.
        projects.recalculateProgress(task.workspaceId(), task.projectId());

        return mapper.toResponse(subtask);
    }

    @Transactional
    public SubtaskResponse update(TaskRef task, UUID subtaskId, UpdateSubtaskRequest request, UUID actorUserId) {
        Subtask subtask = require(task, subtaskId);

        if (request.title() != null) {
            subtask.retitle(requireTitle(request.title()));
        }
        if (Boolean.TRUE.equals(request.clearAssignee())) {
            subtask.clearAssignee();
        } else if (request.assigneeUserId() != null) {
            requireProjectMember(task.projectId(), request.assigneeUserId());
            subtask.assignTo(request.assigneeUserId());
        }
        if (Boolean.TRUE.equals(request.clearDueDate())) {
            subtask.dueOn(null);
        } else if (request.dueDate() != null) {
            subtask.dueOn(request.dueDate());
        }
        if (request.position() != null) {
            subtask.positionAt(request.position());
        }

        events.publishEvent(new SubtaskEvents.SubtaskUpdated(
                task.workspaceId(), task.projectId(), task.taskId(), subtaskId, actorUserId));

        return mapper.toResponse(subtask);
    }

    /**
     * Moves a checklist item through its statuses. DONE is what completing one means.
     *
     * @throws ConflictException naming both statuses, if the move is not legal from here
     */
    @Transactional
    public SubtaskResponse changeStatus(TaskRef task, UUID subtaskId, String target, UUID actorUserId) {
        Subtask subtask = require(task, subtaskId);
        TaskStatus to = parseStatus(target);
        TaskStatus from = subtask.getStatus();

        if (from == to) {
            throw new ConflictException("That subtask is already " + readable(to) + ".");
        }
        if (!from.canMoveTo(to)) {
            throw new ConflictException(
                    "A subtask that is " + readable(from) + " cannot be moved to " + readable(to) + ".");
        }

        subtask.moveTo(to, clock.instant());

        events.publishEvent(new SubtaskEvents.SubtaskStatusChanged(
                task.workspaceId(), task.projectId(), task.taskId(), subtaskId, from, to, actorUserId));

        if (to.isComplete()) {
            events.publishEvent(new SubtaskEvents.SubtaskCompleted(
                    task.workspaceId(), task.projectId(), task.taskId(), subtaskId, actorUserId));
        }

        projects.recalculateProgress(task.workspaceId(), task.projectId());

        return mapper.toResponse(subtask);
    }

    @Transactional
    public void delete(TaskRef task, UUID subtaskId, UUID actorUserId) {
        Subtask subtask = require(task, subtaskId);
        subtask.softDelete(clock.instant());

        events.publishEvent(new SubtaskEvents.SubtaskDeleted(
                task.workspaceId(), task.projectId(), task.taskId(), subtaskId, actorUserId));

        // A removed item leaves both sides of its task's fraction, so the parent
        // project's number moves even though no status changed.
        projects.recalculateProgress(task.workspaceId(), task.projectId());
    }

    private Subtask require(TaskRef task, UUID subtaskId) {
        return subtasks.findByIdAndTaskIdAndDeletedAtIsNull(subtaskId, task.taskId())
                .orElseThrow(() -> ResourceNotFoundException.of("Subtask", subtaskId));
    }

    private void requireProjectMember(UUID projectId, UUID userId) {
        if (!projects.isProjectMember(projectId, userId)) {
            throw new BadRequestException("That person is not a member of this task's project.");
        }
    }

    private static String requireTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty()) {
            throw new BadRequestException("A subtask needs a title.");
        }
        if (title.length() > 200) {
            throw new BadRequestException("A subtask title may be at most 200 characters.");
        }
        return title;
    }

    private static TaskStatus parseStatus(String raw) {
        try {
            return TaskStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("That is not a subtask status.");
        }
    }

    private static String readable(TaskStatus status) {
        return status.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
