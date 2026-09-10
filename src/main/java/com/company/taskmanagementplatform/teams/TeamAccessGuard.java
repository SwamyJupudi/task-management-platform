package com.company.taskmanagementplatform.teams;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.workspaces.WorkspaceAccessGuard;

/**
 * The gate on every team endpoint, and the place the two-layer rule from {@code architecture.md} is
 * actually spelled out.
 *
 * <p>Four questions, always in this order, because each one may only be asked of somebody who has
 * already passed the one before it:
 *
 * <ol>
 *   <li>Can the caller see this workspace at all? No, and the answer is 404, from the workspace
 *       guard. A workspace a stranger has nothing to do with must look like nothing at all.
 *   <li>Do they hold the permission this operation needs? No, and the answer is 403.
 *   <li>Is the workspace still open for changes? Archived, and the answer is 409. Asked only for
 *       operations that change something.
 *   <li>Does this team exist in <em>this</em> workspace, and may they act on this particular one?
 *       Missing or belonging elsewhere is 404; visible but not theirs to change is 403.
 * </ol>
 *
 * <p>The fourth question is the scope layer. Permission answers what a caller may do and scope
 * answers which rows they may do it to, and both must pass. A team lead holds {@code team:update}
 * and so passes the second question for every team in the workspace; they hold {@code
 * team:manage_any} for none of them, so the fourth narrows them to the teams they actually lead. An
 * administrator holds both and reaches all of them.
 *
 * <p>These methods authorize and return nothing. The team is deliberately not handed back, even
 * though one was loaded to answer the fourth question. The guard runs in its own read-only
 * transaction, so anything it returned would reach the service already detached, and every change
 * made to it would be silently discarded at the end of the request. Each service loads the row it
 * intends to change inside the transaction that changes it.
 */
@Component
class TeamAccessGuard {

    private final WorkspaceAccessGuard workspaceGuard;
    private final TeamRepository teams;

    TeamAccessGuard(WorkspaceAccessGuard workspaceGuard, TeamRepository teams) {
        this.workspaceGuard = workspaceGuard;
        this.teams = teams;
    }

    /** Reads over the teams of a workspace, without naming one. */
    @Transactional(readOnly = true)
    public void requireReadAccess(UUID workspaceId) {
        workspaceGuard.requirePermission(workspaceId, Permissions.TEAM_READ);
    }

    /** One team, readable by anybody who may read teams in its workspace. */
    @Transactional(readOnly = true)
    public void requireReadableTeam(UUID workspaceId, UUID teamId) {
        workspaceGuard.requirePermission(workspaceId, Permissions.TEAM_READ);
        requireTeamInWorkspace(workspaceId, teamId);
    }

    /** Creating a team, which names no team yet and so has no scope question to answer. */
    @Transactional(readOnly = true)
    public void requireCreateAccess(UUID workspaceId) {
        workspaceGuard.requirePermissionToChange(workspaceId, Permissions.TEAM_CREATE);
    }

    /**
     * One team the caller may change, resolving the permission and the scope together.
     *
     * <p>The permission set is read once and both questions are answered from it, rather than
     * resolving it twice for what is one decision.
     */
    @Transactional(readOnly = true)
    public void requireChangeableTeam(UUID workspaceId, UUID teamId, String permissionCode) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        if (!granted.contains(permissionCode)) {
            throw new AccessDeniedException("Missing permission " + permissionCode);
        }
        workspaceGuard.requireActiveWorkspace(workspaceId);

        Team team = requireTeamInWorkspace(workspaceId, teamId);
        if (!granted.contains(Permissions.TEAM_MANAGE_ANY) && !team.isLedBy(CurrentUser.requireId())) {
            // Visible to them, so 403 rather than 404. Pretending it were missing
            // would be misleading: they can see it in the list they just fetched.
            throw new AccessDeniedException("Only the lead of this team may change it");
        }
    }

    /**
     * A team of this workspace, or nothing.
     *
     * <p>The workspace is part of the lookup rather than checked afterwards, so a team identifier
     * from another workspace is indistinguishable from one that was never real.
     */
    private Team requireTeamInWorkspace(UUID workspaceId, UUID teamId) {
        return teams.findByIdAndWorkspaceIdAndDeletedAtIsNull(teamId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team", teamId));
    }
}
