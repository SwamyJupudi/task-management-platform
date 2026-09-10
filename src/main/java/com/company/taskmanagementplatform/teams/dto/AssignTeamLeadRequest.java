package com.company.taskmanagementplatform.teams.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Names the team's lead.
 *
 * <p>The person is added to the team if they are not in it already, so a caller never has to make
 * two requests to express one intention.
 */
@Schema(name = "AssignTeamLeadRequest")
public record AssignTeamLeadRequest(
        @NotNull @Schema(description = "Must already be a member of this workspace") UUID userId) {}
