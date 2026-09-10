package com.company.taskmanagementplatform.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Answers what a caller may do, by reading roles and their permissions from the database.
 *
 * <p>Declared here and implemented by the {@code workspaces} module, for the same reason as {@link
 * AccountStatusProvider}: this package must not depend on a module.
 *
 * <p>There is no caching behind either method, and no special case for the platform administrator.
 * {@code SUPER_ADMIN} holds its permissions through ordinary rows in {@code role_permissions}, so
 * one query serves everybody and there is no privileged branch for a test to miss.
 */
public interface PermissionResolver {

    /**
     * Everything the caller may do inside one workspace: the permissions of their platform role, if
     * they hold one, together with those of their role in that workspace, if they are a member.
     */
    Set<String> resolveForWorkspace(UUID userId, UUID workspaceId);

    /**
     * Only what the caller's platform role allows, ignoring every workspace membership. This is what
     * platform-wide endpoints check, so that an administrator of one workspace cannot reach across
     * the whole installation.
     */
    Set<String> resolveForPlatform(UUID userId);
}
