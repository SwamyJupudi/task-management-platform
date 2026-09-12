package com.company.taskmanagementplatform.reports.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The whole workspace at a glance, for somebody entitled to see the whole workspace.
 *
 * <p>Gated on {@code project:read_any}, and narrowed for nobody. A caller without that grant is
 * refused rather than shown a smaller version of this: a workspace-wide figure computed over one
 * person's projects would be a wrong number rather than a discreet one, and it would be wrong without
 * saying so.
 *
 * <p>Assembled in one read-only transaction, so the headline counts and the breakdowns beneath them
 * agree with each other.
 *
 * @param totalMembers everybody on the workspace roster
 * @param membersByRole the same headcount split by role slug, so a client can say how many of the
 *     total are administrators without asking twice. Roles nobody holds are present at zero
 * @param totalTeams live teams
 * @param activeProjects projects in ACTIVE
 * @param completedProjects projects in COMPLETED
 * @param openTasks every task not DONE
 * @param overdueTasks open tasks whose due date has passed where the company is
 * @param taskDistribution every task by status and by priority
 * @param projectProgress the projects with their progress, most recently touched first and capped.
 *     The project report is where somebody pages through all of them
 * @param teamPerformance one line per team, capped the same way
 */
@Schema(name = "AdminDashboard", description = "The whole workspace at a glance")
public record AdminDashboardResponse(
        @Schema(example = "42") long totalMembers,
        @Schema(description = "Headcount by role slug", example = "{\"ADMIN\":2,\"EMPLOYEE\":40}")
                Map<String, Long> membersByRole,
        @Schema(example = "7") long totalTeams,
        @Schema(example = "11") long activeProjects,
        @Schema(example = "24") long completedProjects,
        @Schema(example = "318") long openTasks,
        @Schema(example = "19") long overdueTasks,
        DistributionResponse taskDistribution,
        List<ProjectProgressResponse> projectProgress,
        List<TeamPerformanceResponse> teamPerformance) {}
