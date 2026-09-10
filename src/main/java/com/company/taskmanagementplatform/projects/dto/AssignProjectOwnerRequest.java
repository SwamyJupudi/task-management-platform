package com.company.taskmanagementplatform.projects.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Names the project's owner.
 *
 * <p>The person is added to the project if they are not on it already, so a caller never has to make
 * two requests to express one intention.
 */
@Schema(name = "AssignProjectOwnerRequest")
public record AssignProjectOwnerRequest(
        @NotNull @Schema(description = "Must already be a member of this workspace") UUID userId) {}
