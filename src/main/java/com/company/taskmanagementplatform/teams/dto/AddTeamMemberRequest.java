package com.company.taskmanagementplatform.teams.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** Adds an existing workspace member to a team. */
@Schema(name = "AddTeamMemberRequest")
public record AddTeamMemberRequest(
        @NotNull @Schema(description = "Must already be a member of this workspace") UUID userId) {}
