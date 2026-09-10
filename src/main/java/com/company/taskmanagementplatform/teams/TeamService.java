package com.company.taskmanagementplatform.teams;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.teams.dto.CreateTeamRequest;
import com.company.taskmanagementplatform.teams.dto.TeamResponse;
import com.company.taskmanagementplatform.teams.dto.UpdateTeamRequest;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * Teams: creating them, editing them, and moving them through their lifecycle.
 *
 * <p>Membership and leadership live in {@link TeamMembershipService}. The split follows the shape of
 * the permissions: this class is mostly {@code team:create}, {@code team:update} and {@code
 * team:delete}, and that one is {@code team:manage_members}.
 *
 * <p>Reaching the {@code workspaces} module happens through {@link MembershipService} and never
 * through its tables, which is the rule that keeps the monolith modular.
 *
 * <p>Every method here takes identifiers and loads the row itself. Accepting an entity from the
 * guard instead would look tidier and would not work: the guard authorizes in its own read-only
 * transaction, so the entity would arrive detached and every change to it would be dropped without
 * an error at the end of the request.
 */
@Service
public class TeamService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamMapper mapper;
    private final MembershipService workspaceMembers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    TeamService(
            TeamRepository teams,
            TeamMemberRepository members,
            TeamMapper mapper,
            MembershipService workspaceMembers,
            ApplicationEventPublisher events,
            Clock clock) {
        this.teams = teams;
        this.members = members;
        this.mapper = mapper;
        this.workspaceMembers = workspaceMembers;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates a team, and puts the lead in it if one was named.
     *
     * <p>The lead joining their own team is not a convenience. Removing a member from a team refuses
     * to strand the lead outside it, and that rule only makes sense if a lead is always a member in
     * the first place.
     */
    @Transactional
    public TeamResponse create(UUID workspaceId, CreateTeamRequest request, UUID creatorUserId) {
        String name = requireName(request.name());
        requireNameIsFree(workspaceId, name, null);

        Team team = teams.save(Team.create(workspaceId, name, trimToNull(request.description()), creatorUserId));

        if (request.leadUserId() != null) {
            requireWorkspaceMember(workspaceId, request.leadUserId());
            members.save(TeamMember.join(
                    team.getId(), workspaceId, request.leadUserId(), creatorUserId, clock.instant()));
            team.assignLead(request.leadUserId());
        }

        return mapper.toResponse(team);
    }

    @Transactional
    public TeamResponse update(UUID workspaceId, UUID teamId, UpdateTeamRequest request) {
        Team team = requireTeam(workspaceId, teamId);
        requireNotArchived(team);

        if (request.name() != null) {
            String name = requireName(request.name());
            requireNameIsFree(team.getWorkspaceId(), name, team.getId());
            team.rename(name);
        }

        if (request.description() != null) {
            team.describe(trimToNull(request.description()));
        }

        return mapper.toResponse(team);
    }

    @Transactional
    public TeamResponse archive(UUID workspaceId, UUID teamId) {
        Team team = requireTeam(workspaceId, teamId);
        if (team.isArchived()) {
            throw new ConflictException("That team is already archived.");
        }
        team.archive();
        return mapper.toResponse(team);
    }

    @Transactional
    public TeamResponse unarchive(UUID workspaceId, UUID teamId) {
        Team team = requireTeam(workspaceId, teamId);
        if (!team.isArchived()) {
            throw new ConflictException("That team is not archived.");
        }
        team.unarchive();
        return mapper.toResponse(team);
    }

    /**
     * Hides a team and frees its name for reuse.
     *
     * <p>The roster is left in place. The team row still exists, so the join rows are not orphans,
     * and keeping them is what makes restoring the row by hand a complete restore rather than a
     * partial one. Every read filters on {@code deleted_at}, so none of it is reachable meanwhile.
     *
     * <p>The event goes out first, so anything referring to the team can stand down inside this same
     * transaction rather than being left pointing at a row nothing will return again.
     */
    @Transactional
    public void delete(UUID workspaceId, UUID teamId) {
        Team team = requireTeam(workspaceId, teamId);
        events.publishEvent(new TeamDeletedEvent(workspaceId, teamId));
        team.softDelete(clock.instant());
    }

    /**
     * Whether a live team with this identifier exists in this workspace.
     *
     * <p>For other modules that need to point at a team, which must not reach into this one's
     * repository. It answers the narrow question only, and always by workspace as well as by
     * identifier, so it can never confirm the existence of a team belonging somewhere else.
     */
    @Transactional(readOnly = true)
    public boolean existsInWorkspace(UUID workspaceId, UUID teamId) {
        return teamId != null
                && teams.findByIdAndWorkspaceIdAndDeletedAtIsNull(teamId, workspaceId).isPresent();
    }

    /**
     * The names of several teams at once, for another module rendering a list that names them.
     *
     * <p>Plural on purpose. The single-team version invites a lookup per row, which is how a list
     * endpoint turns into N+1 without anybody noticing until it is in production.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> teamNamesByIds(UUID workspaceId, Collection<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }
        return teams.findAllById(teamIds).stream()
                .filter(team -> team.getWorkspaceId().equals(workspaceId))
                .collect(Collectors.toMap(Team::getId, Team::getName));
    }

    /**
     * The live teams this person leads in one workspace.
     *
     * <p>Read by the projects module, where leading a team is one of the three things that make a
     * project visible to somebody without the workspace-wide read grant.
     */
    @Transactional(readOnly = true)
    public List<UUID> teamIdsLedBy(UUID workspaceId, UUID userId) {
        return teams.findAllByWorkspaceIdAndLeadUserId(workspaceId, userId).stream()
                .filter(team -> !team.isDeleted())
                .map(Team::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> list(UUID workspaceId, TeamStatus status, Pageable pageable) {
        Page<Team> page = status == null
                ? teams.findAllByWorkspaceIdAndDeletedAtIsNull(workspaceId, pageable)
                : teams.findAllByWorkspaceIdAndStatusAndDeletedAtIsNull(workspaceId, status, pageable);

        List<TeamResponse> mapped = mapper.toResponses(page.getContent());
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TeamResponse describe(UUID workspaceId, UUID teamId) {
        return mapper.toResponse(requireTeam(workspaceId, teamId));
    }

    /**
     * The team, managed by the transaction that is about to change it.
     *
     * <p>Scoped by workspace as well as by identifier, so this repeats the guard's 404 rather than
     * trusting that it ran. The guard is the authorization decision; this is the load.
     */
    private Team requireTeam(UUID workspaceId, UUID teamId) {
        return teams.findByIdAndWorkspaceIdAndDeletedAtIsNull(teamId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team", teamId));
    }

    private void requireWorkspaceMember(UUID workspaceId, UUID userId) {
        if (!workspaceMembers.isMember(workspaceId, userId)) {
            throw new BadRequestException("That person is not a member of this workspace.");
        }
    }

    /**
     * @throws ConflictException if another live team in the workspace already folds to this name
     */
    private void requireNameIsFree(UUID workspaceId, String name, UUID excludingTeamId) {
        if (teams.existsByFoldedName(workspaceId, name, excludingTeamId)) {
            throw new ConflictException("A team with that name already exists in this workspace.");
        }
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("A team needs a name.");
        }
        return name;
    }

    private static void requireNotArchived(Team team) {
        if (team.isArchived()) {
            throw new ConflictException("That team is archived. Restore it before making changes.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
