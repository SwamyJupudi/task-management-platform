package com.company.taskmanagementplatform.teams.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Edits a team. Every field is optional; an omitted one is left alone.
 *
 * <p>A blank description clears it. The lead is not editable here: it has its own endpoints, because
 * assigning one also adjusts the roster and that is worth being explicit about rather than a side
 * effect of a general edit.
 */
@Schema(name = "UpdateTeamRequest")
public record UpdateTeamRequest(
        @Size(min = 1, max = 120) @Schema(example = "Platform") String name,
        @Size(max = 500) @Schema(example = "Keeps the platform running") String description) {}
