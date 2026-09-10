package com.company.taskmanagementplatform.teams;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.teams.dto.TeamMemberResponse;
import com.company.taskmanagementplatform.teams.dto.TeamResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * Who is in a team, and which of them leads it.
 *
 * <p>Two invariants hold this together, and each is enforced in the schema as well as here. A team
 * member must be a member of the team's workspace, so somebody removed from the workspace cannot
 * linger in its teams. And the lead is always one of the team's own members, which is why assigning
 * a lead adds them to the team and why removing a member refuses to strand the lead outside it.
 *
 * <p>Like {@link TeamService}, every method takes identifiers and loads the team itself. A team
 * handed over by the guard would already be detached, and the lead assigned to it would never be
 * written.
 */
@Service
public class TeamMembershipService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamMapper mapper;
    private final MembershipService workspaceMembers;
    private final UserAccountService users;
    private final Clock clock;

    TeamMembershipService(
            TeamRepository teams,
            TeamMemberRepository members,
            TeamMapper mapper,
            MembershipService workspaceMembers,
            UserAccountService users,
            Clock clock) {
        this.teams = teams;
        this.members = members;
        this.mapper = mapper;
        this.workspaceMembers = workspaceMembers;
        this.users = users;
        this.clock = clock;
    }

    /**
     * A page of the roster, joined with each person's profile.
     *
     * <p>Profiles are fetched in one call rather than one per row, for the same reason the workspace
     * roster does it: this is the shape of query that turns into N+1 unnoticed.
     */
    @Transactional(readOnly = true)
    public Page<TeamMemberResponse> listMembers(UUID workspaceId, UUID teamId, Pageable pageable) {
        Team team = requireTeam(workspaceId, teamId);
        Page<TeamMember> page = members.findAllByTeamId(team.getId(), pageable);

        Map<UUID, UserAccount> accounts =
                users.findAllByIds(page.getContent().stream().map(TeamMember::getUserId).toList());

        return page.map(member -> toResponse(member, accounts.get(member.getUserId()), team));
    }

    @Transactional
    public TeamMemberResponse addMember(UUID workspaceId, UUID teamId, UUID userId, UUID addedByUserId) {
        Team team = requireTeam(workspaceId, teamId);
        requireActiveTeam(team);
        requireWorkspaceMember(team.getWorkspaceId(), userId);

        if (members.existsByTeamIdAndUserId(team.getId(), userId)) {
            throw new ConflictException("That person is already in this team.");
        }

        TeamMember member = members.save(
                TeamMember.join(team.getId(), team.getWorkspaceId(), userId, addedByUserId, clock.instant()));

        return toResponse(member, users.findById(userId).orElse(null), team);
    }

    /**
     * Takes somebody out of a team.
     *
     * <p>Refuses to remove the lead, rather than quietly clearing the leadership as a side effect.
     * Losing a team's lead is a decision somebody should make deliberately, and a caller who meant to
     * do both can do them in either order through two explicit requests.
     */
    @Transactional
    public void removeMember(UUID workspaceId, UUID teamId, UUID userId) {
        Team team = requireTeam(workspaceId, teamId);
        requireActiveTeam(team);

        TeamMember member = members.findByTeamIdAndUserId(team.getId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team member", userId));

        if (team.isLedBy(userId)) {
            throw new ConflictException("That person leads this team. Assign a different lead first.");
        }

        members.delete(member);
    }

    /**
     * Names the team's lead, adding them to the team if they are not in it yet.
     *
     * <p>Adding them is what keeps the invariant true rather than merely hoped for. The alternative,
     * refusing anybody who is not already a member, would make the common case two requests and would
     * leave the caller to discover the rule from an error.
     */
    @Transactional
    public TeamResponse assignLead(UUID workspaceId, UUID teamId, UUID userId, UUID assignedByUserId) {
        Team team = requireTeam(workspaceId, teamId);
        requireActiveTeam(team);
        requireWorkspaceMember(team.getWorkspaceId(), userId);

        if (!members.existsByTeamIdAndUserId(team.getId(), userId)) {
            members.save(TeamMember.join(
                    team.getId(), team.getWorkspaceId(), userId, assignedByUserId, clock.instant()));
        }

        team.assignLead(userId);
        return mapper.toResponse(team);
    }

    /** Leaves the team without a lead. The person stays a member. */
    @Transactional
    public TeamResponse clearLead(UUID workspaceId, UUID teamId) {
        Team team = requireTeam(workspaceId, teamId);
        requireActiveTeam(team);

        if (team.getLeadUserId() == null) {
            throw new ConflictException("That team has no lead to remove.");
        }

        team.clearLead();
        return mapper.toResponse(team);
    }

    /** The team, managed by the transaction that is about to change it. */
    private Team requireTeam(UUID workspaceId, UUID teamId) {
        return teams.findByIdAndWorkspaceIdAndDeletedAtIsNull(teamId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team", teamId));
    }

    private void requireWorkspaceMember(UUID workspaceId, UUID userId) {
        if (!workspaceMembers.isMember(workspaceId, userId)) {
            throw new BadRequestException("That person is not a member of this workspace.");
        }
    }

    private static void requireActiveTeam(Team team) {
        if (team.isArchived()) {
            throw new ConflictException("That team is archived. Restore it before making changes.");
        }
    }

    private static TeamMemberResponse toResponse(TeamMember member, UserAccount account, Team team) {
        return new TeamMemberResponse(
                member.getUserId(),
                account == null ? null : account.email(),
                account == null ? null : account.firstName(),
                account == null ? null : account.lastName(),
                team.isLedBy(member.getUserId()),
                account == null ? null : account.status().name(),
                member.getJoinedAt());
    }
}
