package com.company.taskmanagementplatform.auth.dto;

import java.util.List;

import com.company.taskmanagementplatform.users.dto.UserResponse;
import com.company.taskmanagementplatform.workspaces.dto.MembershipResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the interface needs to draw itself for the current caller.
 *
 * <p>Workspace permissions are deliberately absent. They differ per workspace, and a single list here
 * would either be wrong or would have to carry every workspace the caller belongs to. The interface
 * asks for the roles of the workspace it is showing.
 */
@Schema(name = "CurrentUser")
public record MeResponse(
        UserResponse user,
        @Schema(description = "Platform role slug, if any", example = "SUPER_ADMIN") String platformRole,
        @Schema(description = "Permissions held platform-wide") List<String> platformPermissions,
        @Schema(description = "Workspaces the caller belongs to") List<MembershipResponse> memberships) {}
