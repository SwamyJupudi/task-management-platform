package com.company.taskmanagementplatform.teams.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Creates a team inside a workspace.
 *
 * <p>Naming a lead is optional and, when given, also makes that person a member of the team. A lead
 * who was not in their own team would be a state nothing else in the model expects.
 */
@Schema(name = "CreateTeamRequest")
public record CreateTeamRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Platform") String name,
        @Size(max = 500) @Schema(example = "Keeps the platform running") String description,
        @Schema(description = "Must already be a member of this workspace") UUID leadUserId) {}
