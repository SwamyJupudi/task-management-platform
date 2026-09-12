package com.company.taskmanagementplatform.reports.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one person is carrying, and what they finished in the period asked about.
 *
 * <p>Effort is summed over open work only. A person's load is what is still on their desk; adding the
 * minutes of everything they ever finished would make the number grow forever and stop describing
 * anything.
 *
 * <p>{@code completedInPeriod} is the one figure bounded by a window rather than by a status, because
 * "how much did they finish" means nothing without saying over what stretch of time.
 *
 * <p>The name and address are resolved once for a whole page rather than once per row. A row for
 * somebody whose account has since been removed carries the identifier and no name, rather than being
 * dropped: their work still exists and still has to be counted somewhere.
 */
@Schema(name = "Workload", description = "One person's open, overdue and completed work")
public record WorkloadResponse(
        UUID userId,
        @Schema(example = "ada@example.com") String email,
        @Schema(example = "Ada Lovelace") String fullName,
        @Schema(example = "9") long open,
        @Schema(example = "3") long inProgress,
        @Schema(example = "2") long overdue,
        @Schema(example = "14") long completedInPeriod,
        @Schema(description = "Estimated minutes across open work", example = "2400") long estimatedMinutes,
        @Schema(description = "Minutes recorded so far against open work", example = "1980") long actualMinutes) {}
