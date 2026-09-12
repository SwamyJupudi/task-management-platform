package com.company.taskmanagementplatform.workspaces;

import java.util.UUID;

/**
 * What this module announces when the platform administrator role moves.
 *
 * <p>Two records for the two highest-privilege writes in the platform. They carry no role
 * identifier, for the same reason {@link PlatformRoleService#assignSuperAdmin} takes none: there is
 * exactly one platform role, naming it would invite naming the wrong one, and a consumer that had to
 * resolve it would be a second place the answer could be wrong.
 *
 * <p>The actor is nullable. The startup bootstrap grants the role with nobody signed in, and that
 * grant is as worth recording as any other.
 */
public final class PlatformRoleEvents {

    private PlatformRoleEvents() {}

    public record Granted(UUID actorUserId, UUID userId) {}

    public record Revoked(UUID actorUserId, UUID userId) {}
}
