package com.company.taskmanagementplatform.projects;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.teams.TeamService;
import com.company.taskmanagementplatform.workspaces.WorkspaceAccessGuard;

/**
 * The gate on every project endpoint. The same shape as {@code TeamAccessGuard}, with one addition.
 *
 * <p>The four questions are unchanged and are asked in the same order, because each may only be put
 * to somebody who has already passed the one before it:
 *
 * <ol>
 *   <li>Can the caller see this workspace at all? No, and the answer is 404, from the workspace
 *       guard.
 *   <li>Do they hold the permission this operation needs? No, and the answer is 403.
 *   <li>Is the workspace still open for changes? Archived, and the answer is 409. Asked only for
 *       operations that change something.
 *   <li>Does this project exist in <em>this</em> workspace, and may they act on this particular one?
 * </ol>
 *
 * <p>The addition is that projects have a scope rule for <em>reading</em> as well as for writing,
 * which teams do not. The requirements say an employee views assigned projects, so a project the
 * caller neither owns, belongs to, nor leads the team of is reported as missing, exactly as a
 * project from another workspace is. That is the same reasoning as everywhere else in the platform:
 * a 403 would confirm the identifier names something real.
 *
 * <p>Write scope narrows the same way. Holding {@code project:update} admits a caller for every
 * project in the workspace; holding {@code project:manage_any} as well is what widens them from the
 * projects they own or lead the team of to all of them.
 *
 * <p>These methods authorize and return nothing. The guard runs in its own read-only transaction, so
 * an entity returned from here would reach the service already detached and every change made to it
 * would be discarded without an error.
 */
@Component
class ProjectAccessGuard {

    private final WorkspaceAccessGuard workspaceGuard;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TeamService teams;

    ProjectAccessGuard(
            WorkspaceAccessGuard workspaceGuard,
            ProjectRepository projects,
            ProjectMemberRepository members,
            TeamService teams) {
        this.workspaceGuard = workspaceGuard;
        this.projects = projects;
        this.members = members;
        this.teams = teams;
    }

    /** Listing projects, which names none and is narrowed by the visibility rule instead. */
    @Transactional(readOnly = true)
    public ProjectVisibility requireListAccess(UUID workspaceId) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        if (!granted.contains(Permissions.PROJECT_READ)) {
            throw new AccessDeniedException("Missing permission " + Permissions.PROJECT_READ);
        }
        return visibilityFrom(workspaceId, granted);
    }

    /** Creating a project, which names none and so has no scope question to answer. */
    @Transactional(readOnly = true)
    public void requireCreateAccess(UUID workspaceId) {
        workspaceGuard.requirePermissionToChange(workspaceId, Permissions.PROJECT_CREATE);
    }

    /**
     * One project the caller may read.
     *
     * @throws ResourceNotFoundException if it does not exist here, or is not one of theirs to see
     */
    @Transactional(readOnly = true)
    public void requireReadableProject(UUID workspaceId, UUID projectId) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        if (!granted.contains(Permissions.PROJECT_READ)) {
            throw new AccessDeniedException("Missing permission " + Permissions.PROJECT_READ);
        }

        Project project = requireProjectInWorkspace(workspaceId, projectId);
        if (!visibilityFrom(workspaceId, granted).unrestricted() && !canReach(project)) {
            // Deliberately indistinguishable from a project that does not exist.
            throw ResourceNotFoundException.of("Project", projectId);
        }
    }

    /**
     * One project the caller may change, resolving the permission and both scopes together.
     *
     * <p>The permission set is read once and every question answered from it, rather than resolving
     * it three times for what is one decision.
     */
    @Transactional(readOnly = true)
    public void requireChangeableProject(UUID workspaceId, UUID projectId, String permissionCode) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        if (!granted.contains(permissionCode)) {
            throw new AccessDeniedException("Missing permission " + permissionCode);
        }
        workspaceGuard.requireActiveWorkspace(workspaceId);

        Project project = requireProjectInWorkspace(workspaceId, projectId);

        // Read scope first: something they may not even see must not answer 403.
        if (!granted.contains(Permissions.PROJECT_READ_ANY) && !canReach(project)) {
            throw ResourceNotFoundException.of("Project", projectId);
        }

        if (!granted.contains(Permissions.PROJECT_MANAGE_ANY) && !ownsOrLeads(project)) {
            // Visible to them, so 403 rather than 404. Pretending it were missing
            // would be misleading: they can see it in the list they just fetched.
            throw new AccessDeniedException("Only the project owner or its team lead may change it");
        }
    }

    /** The reach of a caller who lacks {@code project:read_any}, for the listing query. */
    private ProjectVisibility visibilityFrom(UUID workspaceId, Set<String> granted) {
        UUID userId = CurrentUser.requireId();
        return granted.contains(Permissions.PROJECT_READ_ANY)
                ? ProjectVisibility.everything(userId)
                : ProjectVisibility.assignedOnly(userId, teams.teamIdsLedBy(workspaceId, userId));
    }

    /** Owner, member, or lead of the team the project belongs to. */
    private boolean canReach(Project project) {
        UUID userId = CurrentUser.requireId();
        return project.isOwnedBy(userId)
                || members.existsByProjectIdAndUserId(project.getId(), userId)
                || leadsItsTeam(project, userId);
    }

    /** The write scope: owner, or lead of the team it belongs to. Membership alone is not enough. */
    private boolean ownsOrLeads(Project project) {
        UUID userId = CurrentUser.requireId();
        return project.isOwnedBy(userId) || leadsItsTeam(project, userId);
    }

    private boolean leadsItsTeam(Project project, UUID userId) {
        return project.getTeamId() != null
                && teams.teamIdsLedBy(project.getWorkspaceId(), userId).contains(project.getTeamId());
    }

    /**
     * A project of this workspace, or nothing.
     *
     * <p>The workspace is part of the lookup rather than checked afterwards, so a project identifier
     * from another workspace is indistinguishable from one that was never real.
     */
    private Project requireProjectInWorkspace(UUID workspaceId, UUID projectId) {
        return projects.findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }
}
