package com.company.taskmanagementplatform.reports.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One project as a report shows it: how far along, and what is behind.
 *
 * <p>{@code progress} is the derived column the projects module maintains, read rather than
 * recomputed. There is one rule for what a project's progress means and it lives in one statement; a
 * report that added up tasks itself would be a second rule, and the two would disagree the first time
 * somebody changed either.
 *
 * <p>The task counts beside it come from the tasks module and are deliberately not used to derive the
 * percentage. They answer a different question: progress says how much of the work is done, and
 * {@code overdueTasks} says how much of the rest is late.
 */
@Schema(name = "ProjectProgress", description = "One project's progress and task counts")
public record ProjectProgressResponse(
        UUID projectId,
        @Schema(example = "PLAT") String key,
        @Schema(example = "Platform") String name,
        @Schema(example = "ACTIVE") String status,
        @Schema(description = "The derived percentage, 0 to 100", example = "62") int progress,
        @Schema(example = "48") long totalTasks,
        @Schema(example = "30") long doneTasks,
        @Schema(example = "4") long overdueTasks,
        @Schema(description = "The team running it, where there is one") UUID teamId,
        @Schema(description = "Who owns it, where there is somebody") UUID ownerUserId) {}
