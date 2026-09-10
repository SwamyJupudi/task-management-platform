package com.company.taskmanagementplatform.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * The bean that {@code @PreAuthorize} expressions call, registered under the short name {@code perm}
 * so a rule reads as {@code @PreAuthorize("@perm.inWorkspace(#workspaceId, 'member:invite')")}.
 *
 * <p>A Spring expression against a named bean was chosen over a custom annotation and an aspect of
 * our own. It is less code, it fails the same way Spring's own rules fail, and the resulting {@code
 * AccessDeniedException} already has a handler.
 *
 * <p>Every call is a database read. That is the approved trade: a role change or a removal from a
 * workspace takes effect on the next request rather than when a cache decides to expire.
 */
@Component("perm")
public class PermissionChecker {

    private final PermissionResolver resolver;

    PermissionChecker(PermissionResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Whether the caller holds the permission inside one workspace, whether it reaches them through
     * their membership there or through a platform role.
     */
    public boolean inWorkspace(UUID workspaceId, String permissionCode) {
        Optional<AuthenticatedUser> caller = CurrentUser.find();
        if (caller.isEmpty() || workspaceId == null) {
            return false;
        }
        return resolver.resolveForWorkspace(caller.get().id(), workspaceId).contains(permissionCode);
    }

    /**
     * Whether the caller holds the permission platform-wide.
     *
     * <p>Membership of a workspace is not consulted, so administering one workspace never reaches
     * across the installation.
     */
    public boolean onPlatform(String permissionCode) {
        return CurrentUser.find()
                .map(caller -> resolver.resolveForPlatform(caller.id()).contains(permissionCode))
                .orElse(false);
    }

    /** Whether the caller is the person being acted on. For endpoints about one's own account. */
    public boolean isSelf(UUID userId) {
        return CurrentUser.find().map(caller -> caller.id().equals(userId)).orElse(false);
    }
}
