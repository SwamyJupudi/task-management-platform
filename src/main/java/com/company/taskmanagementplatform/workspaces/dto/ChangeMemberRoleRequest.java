package com.company.taskmanagementplatform.workspaces.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Moves a member to a different role within the same workspace. */
@Schema(name = "ChangeMemberRoleRequest")
public record ChangeMemberRoleRequest(
        @NotBlank @Schema(example = "TEAM_LEAD", description = "Slug of a role in this workspace") String roleSlug) {}
