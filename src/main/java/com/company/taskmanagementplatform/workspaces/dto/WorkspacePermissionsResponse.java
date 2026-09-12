package com.company.taskmanagementplatform.workspaces.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do inside one workspace.
 *
 * <p>Built for the interface, which has to decide what to render before it knows whether a request
 * would be refused. {@code /auth/me} deliberately does not carry this: permissions differ per
 * workspace, so a single list there would either be wrong or would have to carry every workspace the
 * caller belongs to.
 *
 * <p><strong>It answers for the caller and for nobody else.</strong> There is no path, parameter or
 * role that renders one person's permissions to another, which is the same property {@code
 * /dashboard/me} has and for the same reason: the endpoint is given nothing to be wrong about.
 *
 * <p>The set is the union of the caller's platform role, if they hold one, and their role in this
 * workspace, if they are a member. It is resolved on every request with no cache anywhere, so a role
 * edit is reflected here immediately, which is what makes this endpoint correct and a hardcoded
 * role-to-permission map in a client wrong.
 *
 * <p>The role the caller holds is deliberately absent. It is already in the membership list {@code
 * /auth/me} returns, and repeating it here would be a second place for it to go stale.
 *
 * @param workspaceId the workspace these codes apply to, so a response is self-describing and safe
 *     to cache against a key
 * @param permissions every code the caller holds here, sorted, with no duplicates. Sorted so the
 *     body is stable between identical requests rather than reordering with the hash set that
 *     produced it
 */
@Schema(name = "WorkspacePermissions", description = "The permission codes the caller holds in one workspace")
public record WorkspacePermissionsResponse(
        UUID workspaceId,
        @Schema(
                        description = "Permission codes held here, sorted",
                        example = "[\"project:read\", \"task:create\", \"task:read\"]")
                List<String> permissions) {}
