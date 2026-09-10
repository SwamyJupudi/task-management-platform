package com.company.taskmanagementplatform.subtasks;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.subtasks.dto.ChangeSubtaskStatusRequest;
import com.company.taskmanagementplatform.subtasks.dto.CreateSubtaskRequest;
import com.company.taskmanagementplatform.subtasks.dto.SubtaskResponse;
import com.company.taskmanagementplatform.subtasks.dto.UpdateSubtaskRequest;
import com.company.taskmanagementplatform.tasks.TaskAccessGuard;
import com.company.taskmanagementplatform.tasks.TaskRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The checklist under one task.
 *
 * <p>Every method authorizes through {@link TaskAccessGuard} on the parent, which is the whole
 * authorization story for a subtask: there is no subtask permission family, because a subtask is part
 * of a task rather than a thing to hold rights over separately. Editing the checklist needs {@code
 * task:update} and ticking an item off needs {@code task:change_status}, which is exactly what the
 * same two operations need on the task itself.
 *
 * <p>The list is not paginated. A checklist that needs a second page is not a checklist, and the
 * requirements show subtasks as a handful of lines under a task.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/tasks/{taskId}/subtasks")
@Tag(name = "Subtasks", description = "The checklist under a task: completion, assignee, status and due date")
class SubtaskController {

    private final SubtaskService subtasks;
    private final TaskAccessGuard guard;

    SubtaskController(SubtaskService subtasks, TaskAccessGuard guard) {
        this.subtasks = subtasks;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "List the subtasks of a task", description = "In checklist order")
    List<SubtaskResponse> list(@PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        return subtasks.list(guard.requireReadableTask(workspaceId, taskId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a subtask", description = "Starts in TODO, at the end of the checklist")
    SubtaskResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateSubtaskRequest request) {
        TaskRef task = guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_UPDATE);
        return subtasks.create(task, request, CurrentUser.requireId());
    }

    @PatchMapping("/{subtaskId}")
    @Operation(summary = "Edit a subtask", description = "Omitted fields are left alone")
    SubtaskResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @PathVariable UUID subtaskId,
            @Valid @RequestBody UpdateSubtaskRequest request) {
        TaskRef task = guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_UPDATE);
        return subtasks.update(task, subtaskId, request, CurrentUser.requireId());
    }

    /** Ticking an item off, which is what the requirements call completion. */
    @PostMapping("/{subtaskId}/status")
    @Operation(summary = "Change subtask status", description = "DONE records the completion time")
    SubtaskResponse changeStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @PathVariable UUID subtaskId,
            @Valid @RequestBody ChangeSubtaskStatusRequest request) {
        TaskRef task = guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_CHANGE_STATUS);
        return subtasks.changeStatus(task, subtaskId, request.status(), CurrentUser.requireId());
    }

    @DeleteMapping("/{subtaskId}")
    @Operation(summary = "Remove a subtask", description = "Soft delete; it stops counting toward progress")
    ResponseEntity<Void> delete(
            @PathVariable UUID workspaceId, @PathVariable UUID taskId, @PathVariable UUID subtaskId) {
        TaskRef task = guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_UPDATE);
        subtasks.delete(task, subtaskId, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }
}
