package com.company.taskmanagementplatform.workspaces;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.security.PermissionResolver;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Works out what a caller may do, by reading roles and their grants.
 *
 * <p>Three short reads per check: the caller's platform role, their role in the workspace, and the
 * permissions of whichever of those exist. No cache sits in front of them, which is the approved
 * trade. A role change, a removal from a workspace or a deactivation therefore takes effect on the
 * caller's next request, on every instance, with no invalidation to get wrong.
 *
 * <p>Note the absence of a special case. {@code SUPER_ADMIN} is simply a role whose identifier joins
 * the list, and its permissions are rows like anybody else's. Authorization has one path, so a test
 * of that path is a test of the administrator too.
 */
@Component
class PermissionResolverAdapter implements PermissionResolver {

    private final UserAccountService users;
    private final WorkspaceMemberRepository members;
    private final RolePermissionRepository rolePermissions;

    PermissionResolverAdapter(
            UserAccountService users, WorkspaceMemberRepository members, RolePermissionRepository rolePermissions) {
        this.users = users;
        this.members = members;
        this.rolePermissions = rolePermissions;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> resolveForWorkspace(UUID userId, UUID workspaceId) {
        List<UUID> roleIds = new ArrayList<>(2);
        platformRoleId(userId).ifPresent(roleIds::add);
        members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMember::getRoleId)
                .ifPresent(roleIds::add);

        return roleIds.isEmpty() ? Set.of() : rolePermissions.findPermissionCodesByRoleIds(roleIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> resolveForPlatform(UUID userId) {
        // Membership is not consulted on purpose. Administering one workspace must
        // never become a way to act across the whole installation.
        return platformRoleId(userId)
                .map(roleId -> rolePermissions.findPermissionCodesByRoleIds(List.of(roleId)))
                .orElseGet(Set::of);
    }

    /**
     * The caller's platform role, read through the {@code users} module rather than from its table.
     * The identifier is part of {@link UserAccount}, so no entity crosses the boundary.
     */
    private Optional<UUID> platformRoleId(UUID userId) {
        return users.findById(userId).map(UserAccount::platformRoleId);
    }
}
