package com.company.taskmanagementplatform.projects.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Moves a project to another status, if the state machine allows the move. */
@Schema(name = "ChangeProjectStatusRequest")
public record ChangeProjectStatusRequest(
        @NotBlank
                @Schema(
                        example = "ACTIVE",
                        description = "PLANNING, ACTIVE, ON_HOLD, COMPLETED or ARCHIVED")
                String status) {}
