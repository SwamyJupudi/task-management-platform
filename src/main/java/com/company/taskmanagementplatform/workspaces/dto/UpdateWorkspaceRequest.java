package com.company.taskmanagementplatform.workspaces.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Edits the settings of a workspace. Every field is optional; an omitted one is left alone.
 *
 * <p>The slug is absent on purpose. It appears in links that have already been shared, so it is
 * fixed once the workspace exists.
 *
 * <p>A blank description clears it, which is the one way to remove a value through this request.
 * Blank is distinguishable from absent here because the record holds null for a field the client did
 * not send at all.
 */
@Schema(name = "UpdateWorkspaceRequest")
public record UpdateWorkspaceRequest(
        @Size(min = 1, max = 120) @Schema(example = "Platform Team") String name,
        @Size(max = 500) @Schema(example = "The people who keep the platform running") String description,
        @Size(max = 64) @Schema(example = "Europe/London", description = "IANA zone name") String timezone,
        @Schema(example = "EMPLOYEE", description = "Slug of a role in this workspace") String defaultRoleSlug) {}
