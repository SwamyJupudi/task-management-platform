package com.company.taskmanagementplatform.workspaces.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A workspace, with its settings and its lifecycle state.
 *
 * <p>The default role is named by slug rather than by identifier, because a slug is stable across
 * workspaces and is what a client already uses everywhere else roles appear.
 */
@Schema(name = "Workspace")
public record WorkspaceResponse(
        UUID id,
        @Schema(example = "Platform Team") String name,
        @Schema(example = "platform-team", description = "Immutable; it appears in links") String slug,
        @Schema(example = "The people who keep the platform running") String description,
        @Schema(example = "Europe/London", description = "IANA zone name") String timezone,
        @Schema(example = "EMPLOYEE", description = "Role given to an invitation that names none")
                String defaultRoleSlug,
        @Schema(example = "ACTIVE") String status,
        @Schema(description = "When it was archived, if it is") Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {}
