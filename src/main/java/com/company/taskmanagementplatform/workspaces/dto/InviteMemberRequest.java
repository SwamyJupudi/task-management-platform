package com.company.taskmanagementplatform.workspaces.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Invites an address to a workspace.
 *
 * <p>The role is optional. An invitation that names none uses the workspace's default role, which is
 * a setting an administrator controls, so the common case needs no decision per invitation.
 */
@Schema(name = "InviteMemberRequest")
public record InviteMemberRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "person@example.com") String email,
        @Schema(
                        example = "EMPLOYEE",
                        description = "Slug of a role in this workspace; omitted means the workspace default")
                String roleSlug) {}
