package com.company.taskmanagementplatform.tasks.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Moves a task to another status, if the state machine allows the move. */
@Schema(name = "ChangeTaskStatusRequest")
public record ChangeTaskStatusRequest(
        @NotBlank @Schema(example = "IN_PROGRESS", description = "TODO, IN_PROGRESS, REVIEW or DONE") String status) {}
