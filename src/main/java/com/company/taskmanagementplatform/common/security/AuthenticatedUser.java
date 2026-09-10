package com.company.taskmanagementplatform.common.security;

import java.util.UUID;

/**
 * Who the caller is, and nothing else.
 *
 * <p>No address, no name, no role and no permission set. Everything beyond identity is resolved from
 * the database at the moment it is needed, so a change of role or a deactivation takes effect on the
 * next request rather than when a cached copy happens to expire.
 */
public record AuthenticatedUser(UUID id) {

    public AuthenticatedUser {
        if (id == null) {
            throw new IllegalArgumentException("An authenticated user must have an id");
        }
    }
}
