package com.company.taskmanagementplatform.workspaces;

import java.util.Set;

import com.company.taskmanagementplatform.common.security.Permissions;

/**
 * The roles the platform seeds, and what each one may do.
 *
 * <p>{@code SUPER_ADMIN} is not here. It is platform-scoped, there is exactly one of it, and it is
 * written by a migration together with a mapping to every permission in the catalog. The three below
 * are workspace-scoped, so each workspace gets its own copy of them when it is created.
 *
 * <p>The grants stop at what this phase implements. A team lead has no team permissions yet because
 * teams do not exist; those arrive in the migration and the seeder of the phase that builds them.
 */
enum SystemRole {

    /** Runs a workspace: its people, their roles, and its settings. */
    ADMIN(
            "Admin",
            Set.of(
                    Permissions.WORKSPACE_READ,
                    Permissions.WORKSPACE_UPDATE,
                    Permissions.MEMBER_READ,
                    Permissions.MEMBER_INVITE,
                    Permissions.MEMBER_REMOVE,
                    Permissions.MEMBER_ASSIGN_ROLE,
                    Permissions.ROLE_READ,
                    Permissions.ROLE_MANAGE,
                    Permissions.PERMISSION_READ,
                    Permissions.USER_READ)),

    /** Leads people inside a workspace. May see the roster, not change it. */
    TEAM_LEAD("Team Lead", Set.of(Permissions.WORKSPACE_READ, Permissions.MEMBER_READ, Permissions.ROLE_READ)),

    /** Works in the workspace. Sees who else is in it, and nothing administrative. */
    EMPLOYEE("Employee", Set.of(Permissions.WORKSPACE_READ, Permissions.MEMBER_READ));

    static final String SUPER_ADMIN_SLUG = "SUPER_ADMIN";

    private final String displayName;
    private final Set<String> permissionCodes;

    SystemRole(String displayName, Set<String> permissionCodes) {
        this.displayName = displayName;
        this.permissionCodes = permissionCodes;
    }

    String slug() {
        return name();
    }

    String displayName() {
        return displayName;
    }

    Set<String> permissionCodes() {
        return permissionCodes;
    }
}
