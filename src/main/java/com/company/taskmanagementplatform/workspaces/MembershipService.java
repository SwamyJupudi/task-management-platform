package com.company.taskmanagementplatform.workspaces;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final Clock clock;

    MembershipService(
            WorkspaceMemberRepository members,
            WorkspaceRepository workspaces,
            RoleRepository roles,
            UserAccountService users,
            Clock clock) {
        this.members = members;
        this.workspaces = workspaces;
        this.roles = roles;
        this.users = users;
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
     */
    @Transactional
    public void addMember(UUID workspaceId, UUID userId, UUID roleId, UUID invitedByUserId) {
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

    @Transactional
    public void removeMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace member", userId));
        members.delete(member);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID workspaceId, UUID userId) {
        return members.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> roleIdOf(UUID workspaceId, UUID userId) {
        return members.findByWorkspaceIdAndUserId(workspaceId, userId).map(WorkspaceMember::getRoleId);
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
