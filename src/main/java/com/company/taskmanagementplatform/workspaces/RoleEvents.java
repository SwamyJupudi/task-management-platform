package com.company.taskmanagementplatform.workspaces;

import java.util.List;
import java.util.UUID;

/**
 * What this module announces when a role's grants change.
 *
 * <p>The difference rather than the result, and that is deliberate. An audit trail answers "what
 * changed", and a row holding the forty codes a role ended up with says nothing about which two
 * moved. The service computes the two lists and the row carries both; a no-op edit produces neither
 * and therefore no event at all.
 *
 * <p>This is the one administrative event that names a workspace, because a role belongs to one. Its
 * audit row therefore appears in that workspace's own history, which is where somebody wondering why
 * their permissions changed would look, rather than in the platform trail.
 */
public final class RoleEvents {

    private RoleEvents() {}

    /**
     * @param added codes the role now grants and did not before, sorted
     * @param removed codes it granted and no longer does, sorted
     */
    public record PermissionsChanged(
            UUID workspaceId,
            UUID actorUserId,
            UUID roleId,
            String roleSlug,
            List<String> added,
            List<String> removed) {}
}
