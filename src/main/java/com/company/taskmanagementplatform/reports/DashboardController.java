package com.company.taskmanagementplatform.reports;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.reports.dto.AdminDashboardResponse;
import com.company.taskmanagementplatform.reports.dto.EmployeeDashboardResponse;
import com.company.taskmanagementplatform.reports.dto.TeamDashboardResponse;
import com.company.taskmanagementplatform.teams.TeamSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The three dashboards, split from the reports by audience rather than by subject.
 *
 * <p>A dashboard and a report are the same query asked at a different width, which is why they share
 * a module. They are different endpoints because they are different requests: a dashboard takes no
 * parameters and answers a fixed set of panels, a report takes filters and pages. Folding them
 * together would produce one endpoint with a mode switch, and a mode switch is where authorization
 * rules go to get confused.
 *
 * <p>The workspace comes from the path and never from a header, per the tenancy rule. Every method
 * starts at {@link ReportAccessGuard}, which answers 404 for a workspace or a team the caller cannot
 * see and 403 for one they can see but may not summarise.
 *
 * <p>None of these changes anything, so none of them can answer 409 and none asks whether the
 * workspace is archived. An archived workspace is frozen, not hidden, and its history is exactly the
 * thing somebody would want to read after freezing it.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}")
@Tag(name = "Dashboards", description = "The workspace, a team, or your own work at a glance")
class DashboardController {

    private final DashboardService dashboards;
    private final ReportService reports;
    private final ReportAccessGuard guard;

    DashboardController(DashboardService dashboards, ReportService reports, ReportAccessGuard guard) {
        this.dashboards = dashboards;
        this.reports = reports;
        this.guard = guard;
    }

    /**
     * Your own work.
     *
     * <p>Takes no user identifier, deliberately. There is no path, parameter or role that renders one
     * person's dashboard to another, and the cleanest way to guarantee that is to give the endpoint
     * nothing to be wrong about.
     */
    @GetMapping("/dashboard/me")
    @Operation(
            summary = "Your own dashboard",
            description = "Your projects, your tasks, what is late, what is due, and what you did")
    EmployeeDashboardResponse me(@PathVariable UUID workspaceId) {
        ProjectScope scope = guard.requireReadAccess(workspaceId);
        return dashboards.forEmployee(
                workspaceId, CurrentUser.requireId(), scope, reports.zoneOf(workspaceId));
    }

    /**
     * The whole workspace.
     *
     * <p>Needs {@code project:read_any}. Somebody without it is refused rather than shown a narrowed
     * version: a company-wide figure computed over one person's projects would be a wrong number
     * rather than a discreet one.
     */
    @GetMapping("/dashboard/workspace")
    @Operation(
            summary = "The administrator's dashboard",
            description = "Headcount, teams, projects, tasks and team performance across the workspace")
    AdminDashboardResponse workspace(@PathVariable UUID workspaceId) {
        guard.requireWorkspaceDashboard(workspaceId);
        return dashboards.forWorkspace(workspaceId, reports.zoneOf(workspaceId));
    }

    /**
     * One team.
     *
     * <p>Needs {@code team:read}, plus leading that team or holding the workspace-wide project grant.
     * Anybody else gets 404, and so does a team belonging to another workspace, which is
     * indistinguishable from one that was never real.
     */
    @GetMapping("/teams/{teamId}/dashboard")
    @Operation(
            summary = "One team's dashboard",
            description = "The team's projects, work and workload. The same numbers for everyone who can open it")
    TeamDashboardResponse team(@PathVariable UUID workspaceId, @PathVariable UUID teamId) {
        TeamSummary team = guard.requireTeamDashboard(workspaceId, teamId);
        return dashboards.forTeam(workspaceId, team, reports.zoneOf(workspaceId));
    }
}
