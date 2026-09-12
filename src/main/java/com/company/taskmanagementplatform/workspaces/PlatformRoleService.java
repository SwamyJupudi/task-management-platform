package com.company.taskmanagementplatform.workspaces;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.dto.RoleResponse;

/**
 * The platform-scoped role, and the only way to grant it.
 *
 * <p>{@link #assignSuperAdmin} takes no role identifier. That is the point: a caller cannot name the
 * wrong role because it cannot name a role at all, so the class of mistake is removed rather than
 * guarded against. The database backs the same rule independently, through a foreign key on {@code
 * (platform_role_id, platform_role_scope)} that only a platform-scoped role can satisfy.
 *
 * <p>Granting the role changes nothing about how authorization works. {@code SUPER_ADMIN} holds its
 * permissions through ordinary {@code role_permissions} rows and is resolved by the same query as
 * every other role.
 *
 * @see RoleResponse
 */
@Service
public class PlatformRoleService {

    private final RoleRepository roles;
    private final UserAccountService users;
    private final ApplicationEventPublisher events;

    PlatformRoleService(RoleRepository roles, UserAccountService users, ApplicationEventPublisher events) {
        this.roles = roles;
        this.users = users;
        this.events = events;
    }

    /** Grants the platform administrator role. The only supported way to populate the column. */
    @Transactional
    public void assignSuperAdmin(UUID userId) {
        users.assignPlatformRole(userId, superAdminRoleId());
        events.publishEvent(new PlatformRoleEvents.Granted(actor(), userId));
    }

    /**
     * Takes the platform administrator role away.
     *
     * <p>Takes no role identifier either, for the same reason {@link #assignSuperAdmin} does not:
     * there is one platform role, and a method that could name one could name the wrong one.
     *
     * <p>Two refusals live in {@code UserAccountService} rather than here, so they hold however the
     * column is written: you may not revoke your own, and the last holder may not be demoted. An
     * installation with no platform administrator cannot be administered at all, and the startup
     * bootstrap deliberately never resurrects one.
     */
    @Transactional
    public void revokeSuperAdmin(UUID userId) {
        users.assignPlatformRole(userId, null);
        events.publishEvent(new PlatformRoleEvents.Revoked(actor(), userId));
    }

    /**
     * The person behind the request, or null when the platform itself is acting.
     *
     * <p>{@code SuperAdminBootstrap} grants the role at startup with nobody signed in, and that
     * grant is as worth recording as any other. The audit trail already renders a null actor as "The
     * platform".
     */
    private static UUID actor() {
        return com.company.taskmanagementplatform.common.security.CurrentUser.find()
                .map(com.company.taskmanagementplatform.common.security.AuthenticatedUser::id)
                .orElse(null);
    }

    /**
     * @throws IllegalStateException if the seed migration has not run, which would mean the schema and
     *     the application disagree about what exists
     */
    @Transactional(readOnly = true)
    public UUID superAdminRoleId() {
        return roles.findByWorkspaceIdIsNullAndSlug(SystemRole.SUPER_ADMIN_SLUG)
                .filter(role -> role.getScope() == RoleScope.PLATFORM)
                .map(Role::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "The SUPER_ADMIN platform role is missing. V3 seeds it; the database is not at the "
                                + "expected version."));
    }
}
