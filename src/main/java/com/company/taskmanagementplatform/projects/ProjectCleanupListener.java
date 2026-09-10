package com.company.taskmanagementplatform.projects;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.teams.TeamDeletedEvent;
import com.company.taskmanagementplatform.users.UserDeletedEvent;
import com.company.taskmanagementplatform.workspaces.WorkspaceMemberRemovedEvent;

/**
 * Stands the projects module down when something underneath it goes away.
 *
 * <p>Two things here depend on a workspace membership, and both are foreign keys into {@code
 * workspace_members}: the rows saying somebody is on a project, and the column saying they own one.
 * Until both are gone PostgreSQL refuses to delete the membership, so this is the step that makes
 * the removal possible rather than tidying that could be deferred.
 *
 * <p>Ordered first, ahead of the listener in {@code workspaces} that deletes the membership row
 * itself. The flush is the other half: Hibernate may order the statements in a flush by entity type
 * rather than by call order, so the work is forced out before the publisher continues.
 *
 * <p>Ownership is cleared rather than reassigned, because choosing somebody's replacement is not a
 * decision this code is in a position to make. A team deletion clears the project's team for the
 * same reason: the project outlives the team and somebody has to decide where it goes next.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProjectCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectCleanupListener.class);

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    ProjectCleanupListener(ProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    @EventListener
    @Transactional
    void onWorkspaceMemberRemoved(WorkspaceMemberRemovedEvent event) {
        List<Project> owned =
                projects.findAllByWorkspaceIdAndOwnerUserIdAndDeletedAtIsNull(event.workspaceId(), event.userId());
        owned.forEach(Project::clearOwner);

        long removed = members.deleteAllByWorkspaceIdAndUserId(event.workspaceId(), event.userId());
        members.flush();

        if (removed > 0 || !owned.isEmpty()) {
            log.info(
                    "Cleared project state for a removed workspace member: workspaceId={} userId={} projects={} owned={}",
                    event.workspaceId(),
                    event.userId(),
                    removed,
                    owned.size());
        }
    }

    /**
     * The same cleanup, across every workspace at once.
     *
     * <p>A deleted account may have owned projects in several workspaces, and the membership listener
     * in {@code workspaces} removes all of its rows in one statement, so nothing narrower would do.
     */
    @EventListener
    @Transactional
    void onUserDeleted(UserDeletedEvent event) {
        UUID userId = event.userId();

        List<Project> owned = projects.findAllByOwnerUserIdAndDeletedAtIsNull(userId);
        owned.forEach(Project::clearOwner);

        long removed = members.deleteAllByUserId(userId);
        members.flush();

        if (removed > 0 || !owned.isEmpty()) {
            log.info(
                    "Cleared project state for a deleted account: userId={} projects={} owned={}",
                    userId,
                    removed,
                    owned.size());
        }
    }

    /**
     * Detaches projects from a team that has been removed.
     *
     * <p>The foreign key on {@code (team_id, workspace_id)} would not refuse the soft delete, because
     * a soft delete leaves the row in place. Without this the project would keep pointing at a team
     * every read filters out, and would render with a team that appears to have no name.
     */
    @EventListener
    @Transactional
    void onTeamDeleted(TeamDeletedEvent event) {
        List<Project> attached =
                projects.findAllByWorkspaceIdAndTeamIdAndDeletedAtIsNull(event.workspaceId(), event.teamId());
        attached.forEach(Project::clearTeam);
        projects.flush();

        if (!attached.isEmpty()) {
            log.info(
                    "Detached {} project(s) from a deleted team: workspaceId={} teamId={}",
                    attached.size(),
                    event.workspaceId(),
                    event.teamId());
        }
    }
}
