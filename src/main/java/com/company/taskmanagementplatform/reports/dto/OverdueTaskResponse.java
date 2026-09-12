package com.company.taskmanagementplatform.reports.dto;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One late task, with enough beside it to act on without a second request.
 *
 * <p>The project key and the assignee's name are resolved for a whole page in one lookup each. A
 * report is the easiest place in a platform to write an N+1 without noticing, because the rows look
 * small and the loop looks harmless.
 *
 * <p>{@code daysOverdue} is computed against today in the workspace's timezone and returned rather
 * than left to the client. A client in another timezone subtracting dates itself would get a
 * different answer for the same row, and this is the number people sort and escalate by.
 */
@Schema(name = "OverdueTask", description = "One task past its due date and not finished")
public record OverdueTaskResponse(
        UUID taskId,
        @Schema(example = "PLAT-142") String key,
        @Schema(example = "Rotate the signing key") String title,
        UUID projectId,
        @Schema(example = "Platform") String projectName,
        @Schema(description = "Who holds it, or null if nobody does") UUID assigneeUserId,
        @Schema(example = "Ada Lovelace") String assigneeName,
        @Schema(example = "TODO") String status,
        @Schema(example = "HIGH") String priority,
        LocalDate dueDate,
        @Schema(description = "Whole days past the due date, in the workspace's timezone", example = "6")
                long daysOverdue) {}
