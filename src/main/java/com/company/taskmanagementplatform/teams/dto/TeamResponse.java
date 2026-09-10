package com.company.taskmanagementplatform.teams.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A team, with the lead resolved to a name so a list does not need a second call per row.
 *
 * <p>The lead's fields are null when the team has no lead, which is an ordinary state rather than an
 * error: a team between leads still exists and still has members.
 */
@Schema(name = "Team")
public record TeamResponse(
        UUID id,
        UUID workspaceId,
        @Schema(example = "Platform") String name,
        @Schema(example = "Keeps the platform running") String description,
        @Schema(description = "The team lead, or null if there is none") UUID leadUserId,
        @Schema(example = "lead@example.com") String leadEmail,
        @Schema(example = "Ada Lovelace") String leadName,
        @Schema(example = "ACTIVE") String status,
        @Schema(example = "4") long memberCount,
        Instant createdAt,
        Instant updatedAt) {}
