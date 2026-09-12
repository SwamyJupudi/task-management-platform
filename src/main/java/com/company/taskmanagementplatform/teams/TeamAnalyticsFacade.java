package com.company.taskmanagementplatform.teams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the teams module publishes to {@code reports}, and nothing more.
 *
 * <p>A second facade beside {@link TeamService} rather than more methods on it, for the reason the
 * projects and tasks modules each give: the service maps to response types the teams module owns and
 * is called from its own controller, while everything here answers with values another module
 * composes into a different shape. Nothing here authorizes anything; the caller has already passed
 * the team guard.
 *
 * <p><strong>Read-only, always.</strong> No entity leaves this class: a facade runs in its own
 * read-only transaction, so an entity handed out would arrive detached and every change made to it
 * silently discarded.
 */
@Service
public class TeamAnalyticsFacade {

    private final TeamRepository teams;
    private final TeamMemberRepository members;

    TeamAnalyticsFacade(TeamRepository teams, TeamMemberRepository members) {
        this.teams = teams;
        this.members = members;
    }

    /** How many live teams a workspace has, for the administrator's headline figure. */
    @Transactional(readOnly = true)
    public long countTeams(UUID workspaceId) {
        return teams.countByWorkspaceIdAndDeletedAtIsNull(workspaceId);
    }

    /**
     * How many live teams exist across every workspace, for the platform statistics panel.
     *
     * <p>The one method here with no workspace predicate, and it is deliberately a separate method
     * rather than a nullable parameter on the one above. A query that silently counted the whole
     * installation because somebody passed a null identifier is exactly the defect the admin panel
     * has to be incapable of.
     */
    @Transactional(readOnly = true)
    public long countTeamsPlatformWide() {
        return teams.countByDeletedAtIsNull();
    }

    /**
     * Every live team of a workspace, with its roster size, by name.
     *
     * <p>Sizes come from one grouped query rather than a count per team, which is the shape that
     * turns a list into N+1 without anybody noticing until production. The existing roster listing
     * already does it this way.
     *
     * @param limit how many teams to describe. Team performance is a dashboard panel rather than a
     *     listing, so it is bounded here rather than paged
     */
    @Transactional(readOnly = true)
    public List<TeamSummary> activeTeams(UUID workspaceId, int limit) {
        List<Team> found = teams.findAllByWorkspaceIdAndDeletedAtIsNull(
                        workspaceId,
                        PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.ASC, "name")))
                .getContent();

        Map<UUID, Long> sizes = memberCounts(found.stream().map(Team::getId).toList());

        List<TeamSummary> summaries = new ArrayList<>();
        for (Team team : found) {
            summaries.add(toSummary(team, sizes.getOrDefault(team.getId(), 0L)));
        }
        return List.copyOf(summaries);
    }

    /**
     * One live team of one workspace, or nothing.
     *
     * <p>The workspace is part of the lookup rather than checked afterwards, so a team identifier
     * from another workspace is indistinguishable from one that was never real. That is the rule the
     * team repository is written to make unavoidable.
     */
    @Transactional(readOnly = true)
    public Optional<TeamSummary> findSummary(UUID workspaceId, UUID teamId) {
        return teams.findByIdAndWorkspaceIdAndDeletedAtIsNull(teamId, workspaceId)
                .map(team -> toSummary(team, members.countByTeamId(teamId)));
    }

    /** Everybody on one team, for the workload rows of its dashboard. */
    @Transactional(readOnly = true)
    public List<UUID> memberUserIds(UUID teamId) {
        return members.findAllByTeamId(teamId).stream().map(TeamMember::getUserId).toList();
    }

    /** Roster sizes for a set of teams, in one query. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> memberCounts(Collection<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> sizes = new HashMap<>();
        for (Object[] row : members.countByTeamIds(teamIds)) {
            sizes.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return sizes;
    }

    /** The summaries of a set of teams, keyed by identifier, for a page that spans several. */
    @Transactional(readOnly = true)
    public Map<UUID, TeamSummary> summariesOf(UUID workspaceId, Collection<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> sizes = memberCounts(teamIds);

        Map<UUID, TeamSummary> byId = new LinkedHashMap<>();
        for (Team team : teams.findAllByWorkspaceIdAndIdInAndDeletedAtIsNull(workspaceId, teamIds)) {
            byId.put(team.getId(), toSummary(team, sizes.getOrDefault(team.getId(), 0L)));
        }
        return byId;
    }

    private static TeamSummary toSummary(Team team, long memberCount) {
        return new TeamSummary(
                team.getId(), team.getName(), team.getLeadUserId(), team.getStatus(), memberCount);
    }
}
