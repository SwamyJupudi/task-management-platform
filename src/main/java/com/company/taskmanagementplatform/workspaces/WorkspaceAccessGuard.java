package com.company.taskmanagementplatform.workspaces;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.PermissionResolver;

/**
 * The gate on every workspace-scoped endpoint, and the reason those endpoints do not use {@code
 * @PreAuthorize}.
 *
 * <p>The approved rule is that a workspace the caller has nothing to do with answers as missing, not
 * as forbidden, because a forbidden response confirms the identifier names something real and lets
 * an outsider map the installation one guess at a time. A {@code @PreAuthorize} expression cannot
 * express that: it can only permit or deny, and denying produces 403.
 *
 * <p>So the check is explicit and in two stages, which is also the order the two questions actually
 * have:
 *
 * <ol>
 *   <li>Does this workspace exist, and does the caller have any relationship with it? No, to either,
 *       and the answer is 404.
 *   <li>Does the caller hold the permission this operation needs? No, and the answer is 403.
 * </ol>
 *
 * <p>Membership is inferred from the resolved permission set rather than queried again. Every seeded
 * role grants at least {@code workspace:read}, so an empty set means no membership and no platform
 * role, which is precisely the case that must look like nothing at all.
 */
@Component
public class WorkspaceAccessGuard {

    private final WorkspaceRepository workspaces;
    private final PermissionResolver permissions;

    WorkspaceAccessGuard(WorkspaceRepository workspaces, PermissionResolver permissions) {
        this.workspaces = workspaces;
        this.permissions = permissions;
    }

    /**
     * @throws ResourceNotFoundException if the workspace does not exist or the caller cannot see it
     * @throws AccessDeniedException if the caller can see it but may not do this
     */
    @Transactional(readOnly = true)
    public void requirePermission(UUID workspaceId, String permissionCode) {
        Set<String> granted = visiblePermissions(workspaceId);
        if (!granted.contains(permissionCode)) {
            throw new AccessDeniedException("Missing permission " + permissionCode);
        }
    }

    /** Confirms the caller may see the workspace at all, without requiring anything specific. */
    @Transactional(readOnly = true)
    public Set<String> visiblePermissions(UUID workspaceId) {
        UUID userId = CurrentUser.requireId();

        if (workspaces.findByIdAndDeletedAtIsNull(workspaceId).isEmpty()) {
            throw ResourceNotFoundException.of("Workspace", workspaceId);
        }

        Set<String> granted = permissions.resolveForWorkspace(userId, workspaceId);
        if (granted.isEmpty()) {
            // Deliberately indistinguishable from a workspace that does not exist.
            throw ResourceNotFoundException.of("Workspace", workspaceId);
        }
        return granted;
    }
}
