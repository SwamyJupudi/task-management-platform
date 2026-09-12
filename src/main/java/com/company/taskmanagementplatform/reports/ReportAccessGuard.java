package com.company.taskmanagementplatform.reports;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectContext;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.teams.TeamAnalyticsFacade;
import com.company.taskmanagementplatform.teams.TeamSummary;
import com.company.taskmanagementplatform.workspaces.WorkspaceAccessGuard;

/**
 * The gate on every dashboard and every report, and the place this phase's authorization is spelled
 * out rather than scattered across two controllers.
 *
 * <p>The platform's two-layer rule applies here unchanged. <strong>Permission</strong> answers what
 * the caller may do and produces 403; <strong>scope</strong> answers which rows a figure is computed
 * over and produces a smaller number rather than an error. Both always, and in that order, after the
 * workspace guard has answered 404 for a workspace the caller has nothing to do with.
 *
 * <p><strong>No permission was added for this phase.</strong> A report is computed over the caller's
 * project read scope, which is the scope a task listing is already narrowed by, and {@code
 * project:read_any} already widens it to the whole workspace. A {@code report:read_any} beside it
 * would be that grant under a second name with its own resolution path that no test of the first one
 * covers, which is the argument that produced no {@code task:read_any} in phase six and no {@code
 * comment:read} in phase seven.
 *
 * <p>The scope this returns is applied <em>inside</em> each aggregate query, joined to the filters
 * with AND. That is what makes a report incapable of becoming a way past the scope layer, and it is
 * the property {@code ReportVisibilityIT} exists to keep true. A predicate applied after aggregation
 * would be a leak that returns 200 and looks correct.
 */
@Component
class ReportAccessGuard {

    private final WorkspaceAccessGuard workspaceGuard;
    private final ProjectAccessFacade projects;
    private final TeamAnalyticsFacade teams;

    ReportAccessGuard(
            WorkspaceAccessGuard workspaceGuard, ProjectAccessFacade projects, TeamAnalyticsFacade teams) {
        this.workspaceGuard = workspaceGuard;
        this.projects = projects;
        this.teams = teams;
    }

    /**
     * The employee dashboard and every report listing: {@code task:read}, narrowed to what the caller
     * reaches.
     *
     * <p>A caller who reaches no project passes this and gets zeros and empty lists. They belong to
     * the workspace; there is simply nothing of theirs in it. Refusing them would confuse having no
     * work with having no business asking.
     */
    @Transactional(readOnly = true)
    ProjectScope requireReadAccess(UUID workspaceId) {
        Set<String> granted = require(workspaceId, Permissions.TASK_READ);
        return scopeFrom(workspaceId, granted);
    }

    /**
     * The project report, which reads projects rather than tasks and so names the project grant.
     *
     * <p>Both codes, not either. The report shows a project's progress beside counts derived from its
     * tasks, so a caller who could obtain neither half from its own listing should not obtain the pair
     * here.
     */
    @Transactional(readOnly = true)
    ProjectScope requireProjectReadAccess(UUID workspaceId) {
        Set<String> granted = require(workspaceId, Permissions.PROJECT_READ, Permissions.TASK_READ);
        return scopeFrom(workspaceId, granted);
    }

    /**
     * The administrator's dashboard: the workspace-wide grant, plus the two the headcounts come from.
     *
     * <p>{@code project:read_any} is the real gate; {@code member:read} and {@code team:read} are held
     * by every seeded role. They are named anyway so this endpoint cannot return a figure the caller
     * could not have obtained from the listing it summarises.
     *
     * <p>A caller without the workspace-wide grant is refused rather than narrowed, for the reason
     * {@code AdminDashboardResponse} records: a company-wide number computed over one person's
     * projects is wrong rather than discreet.
     */
    @Transactional(readOnly = true)
    void requireWorkspaceDashboard(UUID workspaceId) {
        require(workspaceId, Permissions.PROJECT_READ_ANY, Permissions.MEMBER_READ, Permissions.TEAM_READ);
    }

    /**
     * One team's dashboard: {@code team:read}, plus either leading that team or the workspace-wide
     * project grant.
     *
     * <p>Anybody else gets 404 rather than 403, matching the rule that a record out of reach is
     * reported as missing. A 403 here would confirm that the identifier names a real team of a real
     * workspace to somebody who has no business knowing it.
     *
     * @return the team, already loaded, so the service does not look it up a second time
     */
    @Transactional(readOnly = true)
    TeamSummary requireTeamDashboard(UUID workspaceId, UUID teamId) {
        Set<String> granted = require(workspaceId, Permissions.TEAM_READ);

        TeamSummary team = teams.findSummary(workspaceId, teamId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team", teamId));

        boolean leadsIt = team.leadUserId() != null && team.leadUserId().equals(CurrentUser.requireId());
        if (!leadsIt && !granted.contains(Permissions.PROJECT_READ_ANY)) {
            throw ResourceNotFoundException.of("Team", teamId);
        }
        return team;
    }

    /**
     * Narrows a scope by the {@code projectId} and {@code teamId} filters, without ever widening it.
     *
     * <p>The filters are intersected with the caller's reach rather than replacing it, which is the
     * whole reason this is one method instead of two lines in each controller. A project the caller
     * cannot see answers 404 rather than an empty result: an empty page would say the project exists
     * and happens to hold nothing, which is a different and untrue statement.
     *
     * <p>A team whose projects are all outside the caller's reach leaves an empty scope, and an empty
     * scope matches nothing rather than everything. That distinction is the one the task listing's
     * impossible-identifier trick already guards, and getting it backwards here would turn a filter
     * into a disclosure.
     */
    @Transactional(readOnly = true)
    ProjectScope narrow(UUID workspaceId, ProjectScope scope, UUID projectId, UUID teamId) {
        ProjectScope narrowed = scope;

        if (projectId != null) {
            ProjectContext context = projects
                    .contextOf(workspaceId, projectId, CurrentUser.requireId(), scope.unrestricted())
                    .filter(ProjectContext::readable)
                    .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
            narrowed = intersect(narrowed, List.of(context.projectId()));
        }

        if (teamId != null) {
            teams.findSummary(workspaceId, teamId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Team", teamId));
            narrowed = intersect(narrowed, projects.projectIdsOfTeam(workspaceId, teamId));
        }

        return narrowed;
    }

    private static ProjectScope intersect(ProjectScope scope, List<UUID> projectIds) {
        if (scope.unrestricted()) {
            return ProjectScope.of(projectIds);
        }
        return ProjectScope.of(
                projectIds.stream().filter(scope.projectIds()::contains).toList());
    }

    private Set<String> require(UUID workspaceId, String... permissionCodes) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        for (String code : permissionCodes) {
            if (!granted.contains(code)) {
                throw new AccessDeniedException("Missing permission " + code);
            }
        }
        return granted;
    }

    private ProjectScope scopeFrom(UUID workspaceId, Set<String> granted) {
        return projects.readableScope(
                workspaceId, CurrentUser.requireId(), granted.contains(Permissions.PROJECT_READ_ANY));
    }
}
