package com.company.taskmanagementplatform.workspaces;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Gives a new workspace its own copy of the seeded roles.
 *
 * <p>Roles are per workspace by design: the permission catalog is global because a code names
 * something the application can do, but the mapping from role to permission is what an administrator
 * edits, and one workspace's edits must not reach another's. The cost is three role rows and their
 * grants per workspace, which is nothing.
 *
 * <p>Runs inside the caller's transaction, so a workspace is never created without them.
 */
@Component
class WorkspaceRoleSeeder {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;

    WorkspaceRoleSeeder(
            RoleRepository roles, PermissionRepository permissions, RolePermissionRepository rolePermissions) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
    }

    /** @return the seeded roles, keyed by template, so the caller can pick one for the first member */
    Map<SystemRole, Role> seed(UUID workspaceId) {
        Map<String, UUID> permissionIdsByCode = permissions.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Permission::getCode, Permission::getId));

        Map<SystemRole, Role> created = new EnumMap<>(SystemRole.class);

        for (SystemRole template : SystemRole.values()) {
            Role role = roles.save(Role.workspaceRole(workspaceId, template));
            created.put(template, role);

            List<RolePermission> grants = template.permissionCodes().stream()
                    .map(code -> {
                        UUID permissionId = permissionIdsByCode.get(code);
                        if (permissionId == null) {
                            // The constants and the seeded rows have diverged. Failing here
                            // is better than quietly creating a role that cannot do its job.
                            throw new IllegalStateException(
                                    "Permission " + code + " is named in code but missing from the catalog. "
                                            + "A migration is missing.");
                        }
                        return new RolePermission(role.getId(), permissionId);
                    })
                    .toList();

            rolePermissions.saveAll(grants);
        }

        return created;
    }
}
