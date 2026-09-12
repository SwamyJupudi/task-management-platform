package com.company.taskmanagementplatform.workspaces;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.dto.MemberResponse;
import com.company.taskmanagementplatform.workspaces.dto.MembershipResponse;

/** Who belongs to a workspace, and with what role. */
@Service
public class MembershipService {

    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    private final RoleRepository roles;
    private final UserAccountService users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MembershipService(
            WorkspaceMemberRepository members,
            WorkspaceRepository workspaces,
            RoleRepository roles,
            UserAccountService users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.members = members;
        this.workspaces = workspaces;
        this.roles = roles;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * A page of the roster, joined with each person's profile.
     *
     * <p>The profiles are fetched in one call rather than one per row. A roster is exactly the shape
     * of query that turns into N+1 without anybody noticing until it is in production.
     */
    @Transactional(readOnly = true)
    public Page<MemberResponse> listMembers(UUID workspaceId, Pageable pageable) {
        Page<WorkspaceMember> page = members.findAllByWorkspaceId(workspaceId, pageable);

        Map<UUID, UserAccount> accounts =
                users.findAllByIds(page.getContent().stream().map(WorkspaceMember::getUserId).toList());
        Map<UUID, Role> rolesById = rolesOf(workspaceId);

        return page.map(member -> toResponse(member, accounts.get(member.getUserId()), rolesById.get(member.getRoleId())));
    }

    /** The workspaces a person belongs to, for the endpoint that describes their session. */
    @Transactional(readOnly = true)
    public List<MembershipResponse> membershipsOf(UUID userId) {
        return members.findAllByUserId(userId).stream()
                .map(member -> {
                    Workspace workspace =
                            workspaces.findByIdAndDeletedAtIsNull(member.getWorkspaceId()).orElse(null);
                    Role role = roles.findById(member.getRoleId()).orElse(null);
                    if (workspace == null || role == null) {
                        return null;
                    }
                    return new MembershipResponse(
                            workspace.getId(),
                            workspace.getName(),
                            workspace.getSlug(),
                            role.getSlug(),
                            role.getName());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Adds somebody to a workspace with a role belonging to that workspace.
     *
     * <p>The role is looked up by workspace as well as by identifier, so a role from elsewhere cannot
     * be named. Should that check ever be removed, the composite foreign key behind the table refuses
     * the write anyway; belt and braces, with the braces in the schema.
     *
     * <p>The archived check is here rather than only at the controller because this method is also
     * how an invitation is redeemed, and that path is reached without the workspace guard: the person
     * accepting may not be a member of anything yet. An archived workspace must not quietly gain
     * people through a link issued before it was frozen.
     */
    @Transactional
    public void addMember(UUID workspaceId, UUID userId, UUID roleId, UUID invitedByUserId) {
        requireActiveWorkspace(workspaceId);
        if (members.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new ConflictException("That person is already a member of this workspace.");
        }
        requireRoleInWorkspace(workspaceId, roleId);
        members.save(WorkspaceMember.join(workspaceId, userId, roleId, invitedByUserId, clock.instant()));
    }

    @Transactional
    public MemberResponse changeRole(UUID workspaceId, UUID userId, String roleSlug) {
        WorkspaceMember member = members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace member", userId));

        Role role = roles.findByWorkspaceIdAndSlug(workspaceId, roleSlug)
                .orElseThrow(() -> new BadRequestException("That role does not exist in this workspace."));

        member.changeRole(role.getId());
        return toResponse(member, users.findById(userId).orElse(null), role);
    }

    /**
     * Takes somebody off a workspace roster.
     *
     * <p>The event goes out before the delete, and that order is load-bearing rather than incidental.
     * A team lead and a team member are foreign keys into this table, so anything depending on the
     * membership has to stand down first or PostgreSQL refuses the delete. Listeners run inside this
     * transaction, so the whole thing succeeds or none of it does.
     *
     * <p>The flush is the other half of it. Hibernate is free to order the statements in a flush by
     * entity type rather than by the order the calls were made, so without forcing the listeners'
     * work out first, the delete below could still reach the database ahead of it.
     */
    @Transactional
    public void removeMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace member", userId));

        events.publishEvent(new WorkspaceMemberRemovedEvent(workspaceId, userId));
        members.flush();
        members.delete(member);
    }

    /**
     * How many people are on a workspace roster, for the administrator's headline figure.
     *
     * <p>Counted in the database. A dashboard that loaded every member in order to call
     * {@code size()} on the list would be exactly the waste the requirements name, and it would grow
     * worse as the company did.
     */
    @Transactional(readOnly = true)
    public long countMembers(UUID workspaceId) {
        return members.countByWorkspaceId(workspaceId);
    }

    /**
     * The same headcount broken down by role, keyed by role slug.
     *
     * <p>The requirements say "total employees" and the platform has three working roles, so the
     * split is what lets a dashboard say how many of the total are administrators without a second
     * request.
     *
     * <p><strong>Every role of the workspace is present, including those nobody holds.</strong> The
     * grouped query returns no row for an empty role, which is right for it and wrong for the chart
     * this feeds: a missing column makes a client know the role list to draw the axis, and a role
     * that quietly vanished when its last holder left would read as a role that was never there. The
     * gap is filled here rather than in the reports module because this is where the workspace's own
     * role list is already in hand; asking for it a second time further up would be a second place
     * for it to go stale.
     *
     * @return counts by role slug, for example {@code {ADMIN=2, EMPLOYEE=17, TEAM_LEAD=0}}
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countMembersByRole(UUID workspaceId) {
        Map<UUID, Role> rolesById = rolesOf(workspaceId);

        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Role role : rolesById.values()) {
            counts.put(role.getSlug(), 0L);
        }

        for (Object[] row : members.countByWorkspaceIdGroupedByRole(workspaceId)) {
            Role role = rolesById.get((UUID) row[0]);
            if (role != null) {
                counts.merge(role.getSlug(), ((Number) row[1]).longValue(), Long::sum);
            }
        }
        return Map.copyOf(counts);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID workspaceId, UUID userId) {
        return members.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> roleIdOf(UUID workspaceId, UUID userId) {
        return members.findByWorkspaceIdAndUserId(workspaceId, userId).map(WorkspaceMember::getRoleId);
    }

    private void requireActiveWorkspace(UUID workspaceId) {
        Workspace workspace = workspaces.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace", workspaceId));
        if (workspace.isArchived()) {
            throw new ConflictException("This workspace is archived. Restore it before making changes.");
        }
    }

    private void requireRoleInWorkspace(UUID workspaceId, UUID roleId) {
        roles.findByIdAndWorkspaceId(roleId, workspaceId)
                .orElseThrow(() -> new BadRequestException("That role does not exist in this workspace."));
    }

    private Map<UUID, Role> rolesOf(UUID workspaceId) {
        return roles.findAllByWorkspaceIdOrderBySlugAsc(workspaceId).stream()
                .collect(java.util.stream.Collectors.toMap(Role::getId, java.util.function.Function.identity()));
    }

    private static MemberResponse toResponse(WorkspaceMember member, UserAccount account, Role role) {
        return new MemberResponse(
                member.getUserId(),
                account == null ? null : account.email(),
                account == null ? null : account.firstName(),
                account == null ? null : account.lastName(),
                role == null ? null : role.getSlug(),
                role == null ? null : role.getName(),
                account == null ? null : account.status().name(),
                member.getJoinedAt());
    }
}
