package com.company.taskmanagementplatform.reports.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One team's own numbers, and who is carrying them.
 *
 * <p><strong>Not narrowed per viewer, and that is the decision worth reading twice.</strong> Every
 * other figure in this phase is computed over the caller's project reach. This one is computed over
 * the team's projects, whoever is asking. A team dashboard that showed each viewer only the part of
 * their team they happened to reach would hand two people different numbers under the same heading
 * and label both "team performance", which is worse than refusing one of them.
 *
 * <p>So the gate is narrower instead: leading the team, or holding {@code project:read_any}. Anybody
 * else gets 404, matching the platform rule that an unreachable record is reported as missing rather
 * than as forbidden.
 *
 * <p>A team with no projects reads zero rather than 404. The team exists; there is simply no work
 * under it yet.
 *
 * @param teamId the team these figures are about
 * @param name its name
 * @param leadUserId who leads it, or null for a team between leads
 * @param memberCount how many people are on it
 * @param projectCount how many live projects it runs
 * @param openTasks every task in those projects that is not DONE
 * @param overdueTasks open tasks past their date, where the company is
 * @param completedTasks tasks in those projects that are DONE, all time
 * @param averageProgress the mean of the derived progress column across its projects
 * @param workload one row per team member, including members carrying nothing. A person with no work
 *     is a fact about a team, not a row to leave out
 * @param taskDistribution the team's tasks by status and by priority
 */
@Schema(name = "TeamDashboard", description = "One team's work and who is carrying it")
public record TeamDashboardResponse(
        UUID teamId,
        @Schema(example = "Platform") String name,
        @Schema(description = "The lead, where there is one") UUID leadUserId,
        @Schema(example = "6") long memberCount,
        @Schema(example = "3") long projectCount,
        @Schema(example = "41") long openTasks,
        @Schema(example = "5") long overdueTasks,
        @Schema(example = "112") long completedTasks,
        @Schema(example = "58") int averageProgress,
        List<WorkloadResponse> workload,
        DistributionResponse taskDistribution) {}
