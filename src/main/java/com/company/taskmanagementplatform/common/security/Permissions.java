package com.company.taskmanagementplatform.common.security;

import java.util.Set;

/**
 * Every permission code the application implements, as constants.
 *
 * <p>The codes also exist as rows in the {@code permissions} table, written by a migration. This
 * class is the compile-time half of that pair: an annotation referring to a code that was never
 * seeded is a bug nothing else would catch, so {@code PermissionCatalogIT} asserts the two sides
 * agree.
 *
 * <p>Adding a code here means adding it to a migration, and mapping it to {@code SUPER_ADMIN} in
 * that same migration. There is no bypass branch for the platform administrator, so an unmapped
 * permission is one it does not hold.
 */
public final class Permissions {

    public static final String USER_READ = "user:read";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_ACTIVATE = "user:activate";
    public static final String USER_DEACTIVATE = "user:deactivate";
    public static final String USER_DELETE = "user:delete";

    public static final String WORKSPACE_READ = "workspace:read";
    public static final String WORKSPACE_CREATE = "workspace:create";
    public static final String WORKSPACE_UPDATE = "workspace:update";
    public static final String WORKSPACE_DELETE = "workspace:delete";

    public static final String MEMBER_READ = "member:read";
    public static final String MEMBER_INVITE = "member:invite";
    public static final String MEMBER_REMOVE = "member:remove";
    public static final String MEMBER_ASSIGN_ROLE = "member:assign_role";

    public static final String ROLE_READ = "role:read";
    public static final String ROLE_MANAGE = "role:manage";

    public static final String PERMISSION_READ = "permission:read";

    /** The catalog as data, for the test that compares it against the seeded rows. */
    public static final Set<String> ALL = Set.of(
            USER_READ,
            USER_UPDATE,
            USER_ACTIVATE,
            USER_DEACTIVATE,
            USER_DELETE,
            WORKSPACE_READ,
            WORKSPACE_CREATE,
            WORKSPACE_UPDATE,
            WORKSPACE_DELETE,
            MEMBER_READ,
            MEMBER_INVITE,
            MEMBER_REMOVE,
            MEMBER_ASSIGN_ROLE,
            ROLE_READ,
            ROLE_MANAGE,
            PERMISSION_READ);

    private Permissions() {}
}
