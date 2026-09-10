package com.company.taskmanagementplatform.tasks;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.tasks.dto.AddTaskDependencyRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskDependencyResponse;

/**
 * What one task is waiting for, and what is waiting on it.
 *
 * <p>No permission family of its own. A dependency is a property of a task, so reading them needs
 * {@code task:read} and changing them {@code task:update}, and both go through the same guard as
 * everything else about the task.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/tasks/{taskId}/dependencies")
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Task dependencies",
        description = "Recording that one task waits on another in the same project")
class TaskDependencyController {

    private final TaskDependencyService dependencies;
    private final TaskAccessGuard guard;

    TaskDependencyController(TaskDependencyService dependencies, TaskAccessGuard guard) {
        this.dependencies = dependencies;
        this.guard = guard;
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(
            summary = "List a task's dependencies",
            description = "Both directions: what it waits on, and what waits on it")
    TaskDependencyResponse list(@PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        guard.requireReadableTask(workspaceId, taskId);
        return dependencies.describe(taskId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Record that this task waits on another",
            description = "Refuses itself, a duplicate, another project, and anything that would close a cycle")
    TaskDependencyResponse add(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @Valid @RequestBody AddTaskDependencyRequest request) {
        TaskRef task = guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_UPDATE);
        return dependencies.add(task, request.dependsOnTaskId(), CurrentUser.requireId());
    }

    @DeleteMapping("/{dependsOnTaskId}")
    @io.swagger.v3.oas.annotations.Operation(summary = "Stop this task waiting on another")
    ResponseEntity<Void> remove(
            @PathVariable UUID workspaceId, @PathVariable UUID taskId, @PathVariable UUID dependsOnTaskId) {
        TaskRef task = guard.requireChangeableTask(workspaceId, taskId, Permissions.TASK_UPDATE);
        dependencies.remove(task, dependsOnTaskId, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }
}
