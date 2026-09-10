package com.company.taskmanagementplatform.workspaces.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Invites an address to a workspace with a named role. */
@Schema(name = "InviteMemberRequest")
public record InviteMemberRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "person@example.com") String email,
        @NotBlank @Schema(example = "EMPLOYEE", description = "Slug of a role in this workspace") String roleSlug) {}
