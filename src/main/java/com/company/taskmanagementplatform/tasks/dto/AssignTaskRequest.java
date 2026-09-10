package com.company.taskmanagementplatform.tasks.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Names the person a task belongs to.
 *
 * <p>They must already be a member of the task's project. The database enforces it through a
 * composite key, and the service refuses first so the caller reads a sentence rather than a
 * constraint violation. Clearing the assignee is a DELETE on the same address, not a null here.
 */
@Schema(name = "AssignTaskRequest")
public record AssignTaskRequest(
        @NotNull @Schema(description = "Must already be a member of the task's project") UUID assigneeUserId) {}
