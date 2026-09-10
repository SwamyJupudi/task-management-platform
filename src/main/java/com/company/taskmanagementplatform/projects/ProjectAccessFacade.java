package com.company.taskmanagementplatform.projects;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.teams.TeamService;

/**
 * What the projects module publishes to the modules built on top of it.
 *
 * <p>Everything else in this package is package-private, and this class is the reason that can stay
 * true now that tasks exist. Tasks need four things from projects and would otherwise have to reach
 * into its tables for them: the read scope a caller has over projects, the facts needed to authorize
 * inside one project, the projects belonging to a team, and the trigger that re-derives progress.
 *
 * <p>Every method here answers with values rather than entities, deliberately. A guard runs in its
 * own read-only transaction, so an entity returned from this module would reach the caller already
 * detached, and every change made to it would be discarded at the end of the request without an
 * error.
 */
@Service
public class ProjectAccessFacade {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TeamService teams;
    private final ApplicationEventPublisher events;

    ProjectAccessFacade(
            ProjectRepository projects,
            ProjectMemberRepository members,
            TeamService teams,
            ApplicationEventPublisher events) {
        this.projects = projects;
        this.members = members;
        this.teams = teams;
        this.events = events;
    }

    /**
     * The projects this caller may read, as a scope a query in another module can apply.
     *
     * <p>Three things put a project in reach of somebody without the workspace-wide read grant:
     * owning it, being on it, and leading the team it belongs to. That rule lives here once, and
     * tasks inherit it rather than restating it, which is what keeps task visibility and project
     * visibility from drifting apart.
     *
     * @param unrestricted whether the caller holds {@code project:read_any}
     */
    @Transactional(readOnly = true)
    public ProjectScope readableScope(UUID workspaceId, UUID userId, boolean unrestricted) {
        if (unrestricted) {
            return ProjectScope.everything();
        }

        Set<UUID> reachable = new LinkedHashSet<>(projects.findIdsOwnedOrJoinedBy(workspaceId, userId));

        List<UUID> ledTeamIds = teams.teamIdsLedBy(workspaceId, userId);
        if (!ledTeamIds.isEmpty()) {
            reachable.addAll(projects.findIdsByTeamIds(workspaceId, ledTeamIds));
        }

        return ProjectScope.of(List.copyOf(reachable));
    }

    /**
     * One project of this workspace, with the questions another module's guard has to ask answered.
     *
     * <p>The workspace is part of the lookup rather than checked afterwards, so a project from
     * another workspace is indistinguishable from one that never existed.
     *
     * @return empty when no live project of this workspace has that identifier
     */
    @Transactional(readOnly = true)
    public Optional<ProjectContext> contextOf(UUID workspaceId, UUID projectId, UUID userId, boolean unrestricted) {
        List<Object[]> found = projects.findSummary(workspaceId, projectId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = found.get(0);
        String key = (String) row[2];
        String name = (String) row[3];
        ProjectStatus status = (ProjectStatus) row[4];
        UUID ownerUserId = (UUID) row[5];
        UUID teamId = (UUID) row[6];

        boolean leadsItsTeam = teamId != null && teams.teamIdsLedBy(workspaceId, userId).contains(teamId);
        boolean owns = ownerUserId != null && ownerUserId.equals(userId);
        boolean ownedOrLed = owns || leadsItsTeam;
        boolean readable =
                unrestricted || ownedOrLed || members.existsByProjectIdAndUserId(projectId, userId);

        return Optional.of(new ProjectContext(
                projectId, workspaceId, key, name, status == ProjectStatus.ARCHIVED, readable, ownedOrLed));
    }

    /** The live projects of one team, for the {@code teamId} filter on a task listing. */
    @Transactional(readOnly = true)
    public List<UUID> projectIdsOfTeam(UUID workspaceId, UUID teamId) {
        return projects.findIdsByTeamIds(workspaceId, List.of(teamId));
    }

    /** The project behind a key, so a search for {@code PROJ-12} needs no join. */
    @Transactional(readOnly = true)
    public Optional<UUID> projectIdByKey(UUID workspaceId, String key) {
        List<UUID> found = projects.findIdsByFoldedKey(workspaceId, key);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Whether somebody is on a project.
     *
     * <p>The database enforces this for an assignee through a composite key, and refusing in the
     * service first turns a constraint violation into a sentence somebody can act on.
     */
    @Transactional(readOnly = true)
    public boolean isProjectMember(UUID projectId, UUID userId) {
        return members.existsByProjectIdAndUserId(projectId, userId);
    }

    /**
     * Re-derives the project's progress from its tasks, and announces a change to it.
     *
     * <p>Called by the tasks and subtasks modules after anything that could move the number: a task
     * created, deleted or moved between statuses, and the same three for a subtask. The write itself
     * is one statement, so concurrent callers cannot lose each other's update.
     *
     * <p>The event fires only when the stored value actually moved. Phase eight's dashboards want to
     * hear about real changes, not about every card that was dragged one column to the right.
     */
    @Transactional
    public void recalculateProgress(UUID workspaceId, UUID projectId) {
        int before = currentProgress(workspaceId, projectId);
        projects.recalculateProgress(workspaceId, projectId);
        int after = currentProgress(workspaceId, projectId);

        if (before != after) {
            events.publishEvent(new ProjectEvents.ProjectProgressChanged(workspaceId, projectId, before, after));
        }
    }

    private int currentProgress(UUID workspaceId, UUID projectId) {
        List<Integer> found = projects.findProgress(workspaceId, projectId);
        return found.isEmpty() ? 0 : found.get(0);
    }
}
