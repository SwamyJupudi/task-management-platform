package com.company.taskmanagementplatform.workspaces;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.workspaces.dto.PermissionResponse;
import com.company.taskmanagementplatform.workspaces.dto.RoleResponse;

/**
 * Reads over roles and the permission catalog.
 *
 * <p>Reads only. Editing the mapping from role to permission is what the admin panel does, and that
 * belongs to the phase that builds it. The interface needs to be able to show the roles now.
 */
@Service
public class RoleQueryService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;

    RoleQueryService(
            RoleRepository roles, PermissionRepository permissions, RolePermissionRepository rolePermissions) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listWorkspaceRoles(UUID workspaceId) {
        List<Role> found = roles.findAllByWorkspaceIdOrderBySlugAsc(workspaceId);

        Map<UUID, String> codesById = permissions.findAll().stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getCode));

        return found.stream().map(role -> toResponse(role, codesById)).toList();
    }

    /** The slug of one role, for describing a caller's platform role without exposing the entity. */
    @Transactional(readOnly = true)
    public java.util.Optional<String> roleSlug(UUID roleId) {
        return roleId == null ? java.util.Optional.empty() : roles.findById(roleId).map(Role::getSlug);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listCatalog() {
        return permissions.findAllByOrderByCodeAsc().stream()
                .map(permission -> new PermissionResponse(
                        permission.getCode(),
                        permission.getResource(),
                        permission.getAction(),
                        permission.getDescription()))
                .toList();
    }

    private RoleResponse toResponse(Role role, Map<UUID, String> codesById) {
        List<String> granted = rolePermissions.findAllByIdRoleId(role.getId()).stream()
                .map(grant -> codesById.get(grant.getId().getPermissionId()))
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();

        return new RoleResponse(
                role.getId(), role.getSlug(), role.getName(), role.getScope().name(), role.isSystem(), granted);
    }
}
