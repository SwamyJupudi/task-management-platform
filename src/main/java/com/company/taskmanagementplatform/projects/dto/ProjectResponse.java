package com.company.taskmanagementplatform.projects.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A project, with the owner and team resolved so a listing needs no second call per row.
 *
 * <p>The owner fields are null when the project has no owner, which is an ordinary state rather than
 * an error: a project between owners still exists and still has members.
 */
@Schema(name = "Project")
public record ProjectResponse(
        UUID id,
        UUID workspaceId,
        @Schema(example = "PLAT") String key,
        @Schema(example = "Platform Rebuild") String name,
        String description,
        @Schema(description = "The owner, or null if there is none") UUID ownerUserId,
        @Schema(example = "owner@example.com") String ownerEmail,
        @Schema(example = "Ada Lovelace") String ownerName,
        @Schema(description = "The team this project belongs to, or null") UUID teamId,
        @Schema(example = "Platform") String teamName,
        @Schema(example = "ACTIVE") String status,
        @Schema(example = "HIGH") String priority,
        LocalDate startDate,
        LocalDate endDate,
        @Schema(description = "Tags on this project") List<String> labels,
        @Schema(
                        description = "Share of work complete. Derived from tasks in a later phase; zero until then",
                        example = "0")
                int progress,
        @Schema(example = "7") long memberCount,
        Instant createdAt,
        Instant updatedAt) {}
