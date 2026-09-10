package com.company.taskmanagementplatform.workspaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What somebody holding an invitation link is shown before they accept it.
 *
 * <p>Deliberately thin. Anyone with the link can read this without signing in, so it carries the
 * workspace name and nothing that would describe its people or its work.
 */
@Schema(name = "InvitationPreview")
public record InvitationPreviewResponse(
        @Schema(example = "Platform Team") String workspaceName,
        @Schema(example = "person@example.com") String email,
        @Schema(example = "EMPLOYEE") String roleSlug,
        @Schema(description = "Whether an account already exists for this address") boolean accountExists) {}
