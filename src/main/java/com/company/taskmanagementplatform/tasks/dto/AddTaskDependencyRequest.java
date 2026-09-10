package com.company.taskmanagementplatform.tasks.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Records that this task waits on another one.
 *
 * <p>The other task must be in the same project. That is enforced by the database rather than
 * checked here, and it closes an information leak as well as keeping the model simple: a dependency
 * reaching into another project would render a blocker's identifier to somebody who cannot see the
 * project it lives in.
 */
@Schema(name = "AddTaskDependencyRequest")
public record AddTaskDependencyRequest(
        @NotNull @Schema(description = "The task this one waits on; must be in the same project")
                UUID dependsOnTaskId) {}
