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
 * <p>These sets are one half of a pair. The other half is the backfill in {@code V4}, which gives
 * the same grants to workspaces that already existed when teams arrived. {@code
 * WorkspaceRoleGrantsIT} holds the two together, because a workspace created before a phase and one
 * created after it must be able to do the same things.
 */
enum SystemRole {

    /** Runs a workspace: its people, their roles, and its settings. */
    ADMIN(
            "Admin",
            Set.of(
                    Permissions.WORKSPACE_READ,
                    Permissions.WORKSPACE_UPDATE,
                    Permissions.WORKSPACE_ARCHIVE,
                    Permissions.MEMBER_READ,
                    Permissions.MEMBER_INVITE,
                    Permissions.MEMBER_REMOVE,
                    Permissions.MEMBER_ASSIGN_ROLE,
                    Permissions.ROLE_READ,
                    Permissions.ROLE_MANAGE,
                    Permissions.PERMISSION_READ,
                    Permissions.USER_READ,
                    Permissions.TEAM_READ,
                    Permissions.TEAM_CREATE,
                    Permissions.TEAM_UPDATE,
                    Permissions.TEAM_DELETE,
                    Permissions.TEAM_MANAGE_MEMBERS,
                    Permissions.TEAM_MANAGE_ANY,
                    Permissions.PROJECT_READ,
                    Permissions.PROJECT_READ_ANY,
                    Permissions.PROJECT_CREATE,
                    Permissions.PROJECT_UPDATE,
                    Permissions.PROJECT_DELETE,
                    Permissions.PROJECT_MANAGE_MEMBERS,
                    Permissions.PROJECT_MANAGE_ANY)),

    /**
     * Leads people inside a workspace. May see the roster, not change it.
     *
     * <p>Holds the two team-management codes but not {@code team:manage_any}, which is what narrows
     * them to the teams this person actually leads. Creating and removing a team stay with the
     * administrator.
     *
     * <p>Projects follow the same shape and for the same reason. They may edit a project and manage
     * its members, but hold neither {@code project:manage_any}, which would widen that to every
     * project, nor {@code project:read_any}, which would show them projects they have nothing to do
     * with. Creating and removing a project stay with the administrator, who is the project manager
     * the requirements describe.
     */
    TEAM_LEAD(
            "Team Lead",
            Set.of(
                    Permissions.WORKSPACE_READ,
                    Permissions.MEMBER_READ,
                    Permissions.ROLE_READ,
                    Permissions.TEAM_READ,
                    Permissions.TEAM_UPDATE,
                    Permissions.TEAM_MANAGE_MEMBERS,
                    Permissions.PROJECT_READ,
                    Permissions.PROJECT_UPDATE,
                    Permissions.PROJECT_MANAGE_MEMBERS)),

    /** Works in the workspace. Sees who else is in it, and nothing administrative. */
    EMPLOYEE(
            "Employee",
            Set.of(
                    Permissions.WORKSPACE_READ,
                    Permissions.MEMBER_READ,
                    Permissions.TEAM_READ,
                    Permissions.PROJECT_READ));

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
