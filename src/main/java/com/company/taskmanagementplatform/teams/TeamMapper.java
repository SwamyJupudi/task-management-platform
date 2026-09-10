package com.company.taskmanagementplatform.teams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.teams.dto.TeamResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Turns teams into response bodies, resolving the lead's profile and the roster size.
 *
 * <p>Both of those are per-row lookups if written naively, and a page of twenty teams would then be
 * forty-one queries. The batch method takes the whole page and does three.
 */
@Component
class TeamMapper {

    private final UserAccountService users;
    private final TeamMemberRepository members;

    TeamMapper(UserAccountService users, TeamMemberRepository members) {
        this.users = users;
        this.members = members;
    }

    TeamResponse toResponse(Team team) {
        UserAccount lead =
                team.getLeadUserId() == null ? null : users.findById(team.getLeadUserId()).orElse(null);

        return build(team, lead, members.countByTeamId(team.getId()));
    }

    List<TeamResponse> toResponses(List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }

        List<UUID> leadIds = teams.stream()
                .map(Team::getLeadUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, UserAccount> leads = leadIds.isEmpty() ? Map.of() : users.findAllByIds(leadIds);
        Map<UUID, Long> counts = memberCounts(teams.stream().map(Team::getId).toList());

        return teams.stream()
                .map(team -> build(
                        team,
                        team.getLeadUserId() == null ? null : leads.get(team.getLeadUserId()),
                        counts.getOrDefault(team.getId(), 0L)))
                .toList();
    }

    private Map<UUID, Long> memberCounts(List<UUID> teamIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : members.countByTeamIds(teamIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static TeamResponse build(Team team, UserAccount lead, long memberCount) {
        return new TeamResponse(
                team.getId(),
                team.getWorkspaceId(),
                team.getName(),
                team.getDescription(),
                team.getLeadUserId(),
                lead == null ? null : lead.email(),
                lead == null ? null : lead.fullName(),
                team.getStatus().name(),
                memberCount,
                team.getCreatedAt(),
                team.getUpdatedAt());
    }
}
