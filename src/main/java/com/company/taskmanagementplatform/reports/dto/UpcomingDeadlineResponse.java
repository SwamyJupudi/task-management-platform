package com.company.taskmanagementplatform.reports.dto;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the caller's own deadlines inside the lead window.
 *
 * <p>Smaller than {@link OverdueTaskResponse} on purpose. This panel answers "what is coming up for
 * me this week", where the caller already knows who holds the work, so a column repeating their own
 * name in every row would only take space from the titles.
 *
 * <p>{@code daysRemaining} is zero for something due today rather than negative or absent. Today is
 * inside the window, and a task due at the end of the day somebody is reading their dashboard is the
 * most useful row on it.
 */
@Schema(name = "UpcomingDeadline", description = "One of your tasks due soon")
public record UpcomingDeadlineResponse(
        UUID taskId,
        @Schema(example = "PLAT-142") String key,
        @Schema(example = "Rotate the signing key") String title,
        UUID projectId,
        @Schema(example = "HIGH") String priority,
        LocalDate dueDate,
        @Schema(description = "Whole days until it is due; zero means today", example = "2") long daysRemaining) {}
