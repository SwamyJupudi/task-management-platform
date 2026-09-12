package com.company.taskmanagementplatform.reports.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One team's line on the administrator's dashboard.
 *
 * <p>Task counts are over the team's projects rather than over its members. A team is responsible for
 * the work in the projects it runs, including work assigned to somebody borrowed from elsewhere, and
 * excluding work its members do on other teams' projects. Counting by membership instead would make a
 * team's figures change when somebody was lent out for a week.
 *
 * <p>{@code averageProgress} is the mean of the derived progress column across those projects,
 * computed in the database in {@code numeric}, so a team whose projects are all finished reads a
 * hundred rather than ninety-nine.
 */
@Schema(name = "TeamPerformance", description = "One team's projects and the work in them")
public record TeamPerformanceResponse(
        UUID teamId,
        @Schema(example = "Platform") String name,
        @Schema(description = "The lead, where there is one") UUID leadUserId,
        @Schema(example = "6") long memberCount,
        @Schema(example = "3") long projectCount,
        @Schema(example = "41") long openTasks,
        @Schema(example = "5") long overdueTasks,
        @Schema(example = "112") long completedTasks,
        @Schema(description = "Mean progress across the team's projects", example = "58") int averageProgress) {}
