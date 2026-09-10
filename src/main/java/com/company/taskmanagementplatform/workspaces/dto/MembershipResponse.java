package com.company.taskmanagementplatform.workspaces.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A workspace the caller belongs to, and the role they hold there.
 *
 * <p>Returned by the endpoint describing the current session, so the interface can offer a workspace
 * switcher and decide what to render without a second round trip.
 */
@Schema(name = "Membership")
public record MembershipResponse(
        UUID workspaceId,
        @Schema(example = "Platform Team") String workspaceName,
        @Schema(example = "platform-team") String workspaceSlug,
        @Schema(example = "ADMIN") String roleSlug,
        @Schema(example = "Admin") String roleName) {}
