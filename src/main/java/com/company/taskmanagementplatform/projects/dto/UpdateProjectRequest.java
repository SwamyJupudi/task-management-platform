package com.company.taskmanagementplatform.projects.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Edits a project. Every field is optional; an omitted one is left alone.
 *
 * <p>A blank description clears it. A labels list replaces the whole set, and an empty list clears
 * it, because tags are a set rather than a sequence of additions.
 *
 * <p>Status is absent on purpose: it has its own endpoint, because a transition is checked against
 * the state machine and is worth being explicit about rather than happening as a side effect of a
 * general edit. Owner is absent for the same reason, since naming one also adjusts the roster.
 */
@Schema(name = "UpdateProjectRequest")
public record UpdateProjectRequest(
        @Size(min = 1, max = 120) @Schema(example = "Platform Rebuild") String name,
        @Size(max = 2000) String description,
        @Schema(description = "Must be a team of this workspace; not sent leaves it alone") UUID teamId,
        @Schema(description = "Send true to detach the project from its team") Boolean clearTeam,
        @Schema(example = "HIGH") String priority,
        LocalDate startDate,
        LocalDate endDate,
        @Schema(description = "Replaces every tag on the project") List<String> labels) {}
