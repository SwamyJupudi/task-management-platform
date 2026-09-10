package com.company.taskmanagementplatform.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.labels.LabelCatalog;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.tasks.dto.AssignTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.ChangeTaskStatusRequest;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.tasks.dto.UpdateTaskRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The tasks of one workspace.
 *
 * <p>The workspace comes from the path and never from a header, so it can never be inherited from
 * ambient state. Every method starts at {@link TaskAccessGuard}, which answers 404 for a workspace,
 * a project or a task the caller cannot see, 403 for one they can see but may not act on, and 409
 * for a workspace or project that has been archived.
 *
 * <p>Creating is nested under a project, because the project is required and is what the task is
 * numbered against. Everything after that is flat: a task identifier is unique across the platform,
 * and nesting every address under a project would mean a link from a notification had to carry one.
 *
 * <p>A listing returns what the caller may see rather than everything in the workspace. That is not
 * a filter a client applies; the guard decides it and the query enforces it.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}")
@Tag(name = "Tasks", description = "Tasks within a workspace, their lifecycle, assignment and search")
class TaskController {

    /** The rendered form of a task identifier, as somebody would type it into a search box. */
    private static final Pattern TASK_KEY = Pattern.compile("^([A-Za-z][A-Za-z0-9]{1,9})-(\\d{1,9})$");

    private final TaskService tasks;
    private final TaskAccessGuard guard;
    private final ProjectAccessFacade projects;
    private final LabelCatalog labels;

    TaskController(
            TaskService tasks, TaskAccessGuard guard, ProjectAccessFacade projects, LabelCatalog labels) {
        this.tasks = tasks;
        this.guard = guard;
        this.projects = projects;
        this.labels = labels;
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a task", description = "Always starts in TODO, numbered as PROJECTKEY-n")
    TaskResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest request) {
        return tasks.create(
                workspaceId, guard.requireCreateAccess(workspaceId, projectId), request, CurrentUser.requireId());
    }

    /** The board and list view of one project. */
    @GetMapping("/projects/{projectId}/tasks")
    @Operation(summary = "List the tasks of one project", description = "The board and list views")
    PageResponse<TaskResponse> listForProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) UUID reporterUserId,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) LocalDate dueAfter,
            @RequestParam(required = false) LocalDate dueOn,
            @RequestParam(required = false, defaultValue = "false") boolean overdue,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) String label,
            @RequestParam(required = false, defaultValue = "false") boolean blocked,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {

        ProjectScope scope = guard.requireProjectListAccess(workspaceId, projectId);

        TaskFilter filter = buildFilter(
                workspaceId,
                List.of(projectId),
                null,
                status,
                priority,
                assigneeUserId,
                unassigned,
                reporterUserId,
                dueBefore,
                dueAfter,
                dueOn,
                overdue,
                createdBefore,
                createdAfter,
                label,
                blocked,
                q);

        return PageResponse.of(
                tasks.list(workspaceId, filter, scope, pageable, CurrentUser.requireId()), task -> task);
    }

    /**
     * Everything the caller may see across the workspace: My Tasks, the calendar, and global search.
     *
     * <p>Sorting is restricted to an allowlist. Passing the request straight through would let a query
     * parameter probe the shape of the entity and order by columns with no index behind them.
     */
    @GetMapping("/tasks")
    @Operation(
            summary = "Search tasks across a workspace",
            description = "Returns tasks in assigned projects unless the caller may read every project")
    PageResponse<TaskResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) UUID reporterUserId,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) LocalDate dueAfter,
            @RequestParam(required = false) LocalDate dueOn,
            @RequestParam(required = false, defaultValue = "false") boolean overdue,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) String label,
            @RequestParam(required = false, defaultValue = "false") boolean blocked,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {

        ProjectScope scope = guard.requireListAccess(workspaceId);

        TaskFilter filter = buildFilter(
                workspaceId,
                projectId == null ? List.of() : List.of(projectId),
                teamId,
                status,
                priority,
                assigneeUserId,
                unassigned,
                reporterUserId,
                dueBefore,
                dueAfter,
                dueOn,
                overdue,
                createdBefore,
                createdAfter,
                label,
                blocked,
                q);

        return PageResponse.of(
                tasks.list(workspaceId, filter, scope, pageable, CurrentUser.requireId()), task -> task);
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Fetch one task")
    TaskResponse get(@PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        guard.requireReadableTask(workspaceId, taskId);
        return tasks.describe(workspaceId, taskId, CurrentUser.requireId());
    }

    @PatchMapping("/tasks/{taskId}")
    @Operation(summary = "Edit a task", description = "Omitted fields are left alone; labels replace the set")
    TaskResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_UPDATE);
        return tasks.update(workspaceId, taskId, request, CurrentUser.requireId());
    }

    /**
     * Moves a task through its statuses.
     *
     * <p>Its own endpoint rather than a field on the edit, because a transition is checked against the
     * state machine, a rejected one is a 409 rather than a validation error, and it needs a different
     * permission from editing the rest of the task.
     */
    @PostMapping("/tasks/{taskId}/status")
    @Operation(summary = "Change task status", description = "Refuses a move the lifecycle does not allow")
    TaskResponse changeStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @Valid @RequestBody ChangeTaskStatusRequest request) {
        guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_CHANGE_STATUS);
        return tasks.changeStatus(workspaceId, taskId, request.status(), CurrentUser.requireId());
    }

    /**
     * Gives a task to somebody.
     *
     * <p>{@code task:assign} is held by the administrator and the team lead and not by an employee,
     * so this is the task operation an employee cannot perform on their own work.
     */
    @PutMapping("/tasks/{taskId}/assignee")
    @Operation(summary = "Assign a task", description = "The person must already be on the task's project")
    TaskResponse assign(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @Valid @RequestBody AssignTaskRequest request) {
        guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_ASSIGN);
        return tasks.assign(workspaceId, taskId, request.assigneeUserId(), CurrentUser.requireId());
    }

    @DeleteMapping("/tasks/{taskId}/assignee")
    @Operation(summary = "Leave a task unassigned")
    TaskResponse unassign(@PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_ASSIGN);
        return tasks.unassign(workspaceId, taskId, CurrentUser.requireId());
    }

    /**
     * Removes a task.
     *
     * <p>{@code task:delete} is held by the workspace administrator and not by a team lead, so this is
     * the one task operation a lead cannot perform in a project they run.
     */
    @DeleteMapping("/tasks/{taskId}")
    @Operation(summary = "Remove a task", description = "Soft delete; the number is never reused")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_DELETE);
        tasks.delete(workspaceId, taskId, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Turns the query parameters into a filter, resolving what has to become an identifier.
     *
     * <p>A tag name and a {@code PROJ-12} search both name something the query stores as an
     * identifier. Resolving them once here costs one query each and saves a join per row.
     */
    private TaskFilter buildFilter(
            UUID workspaceId,
            List<UUID> projectIds,
            UUID teamId,
            List<String> status,
            List<String> priority,
            UUID assigneeUserId,
            boolean unassigned,
            UUID reporterUserId,
            LocalDate dueBefore,
            LocalDate dueAfter,
            LocalDate dueOn,
            boolean overdue,
            Instant createdBefore,
            Instant createdAfter,
            String label,
            boolean blocked,
            String q) {

        List<UUID> narrowed = new ArrayList<>(projectIds);
        if (teamId != null) {
            List<UUID> ofTeam = projects.projectIdsOfTeam(workspaceId, teamId);
            if (narrowed.isEmpty()) {
                // A team with no projects must match nothing rather than everything,
                // so an impossible identifier stands in for the empty list.
                narrowed.addAll(ofTeam.isEmpty() ? List.of(new UUID(0L, 0L)) : ofTeam);
            } else {
                narrowed.retainAll(ofTeam);
                if (narrowed.isEmpty()) {
                    narrowed.add(new UUID(0L, 0L));
                }
            }
        }

        Optional<UUID> labelId = label == null || label.isBlank()
                ? Optional.empty()
                : labels.findId(workspaceId, label);
        boolean labelUnknown = label != null && !label.isBlank() && labelId.isEmpty();

        return new TaskFilter(
                narrowed,
                assigneeUserId,
                unassigned,
                reporterUserId,
                parseStatuses(status),
                parsePriorities(priority),
                dueBefore,
                dueAfter,
                dueOn,
                overdue,
                createdBefore,
                createdAfter,
                labelId.orElse(null),
                labelUnknown,
                blocked,
                q,
                keyMatch(workspaceId, q));
    }

    /** Resolves a {@code PROJ-12} search term to the project and number the table stores. */
    private TaskFilter.TaskKeyMatch keyMatch(UUID workspaceId, String q) {
        if (q == null) {
            return null;
        }
        Matcher matcher = TASK_KEY.matcher(q.trim());
        if (!matcher.matches()) {
            return null;
        }
        return projects
                .projectIdByKey(workspaceId, matcher.group(1).toUpperCase(Locale.ROOT))
                .map(projectId -> new TaskFilter.TaskKeyMatch(projectId, Integer.parseInt(matcher.group(2))))
                .orElse(null);
    }

    private static List<TaskStatus> parseStatuses(List<String> raw) {
        return raw == null ? List.of() : raw.stream().map(TaskService::parseStatus).toList();
    }

    private static List<TaskPriority> parsePriorities(List<String> raw) {
        return raw == null ? List.of() : raw.stream().map(TaskService::parsePriority).toList();
    }
}
