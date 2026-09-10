package com.company.taskmanagementplatform.workspaces.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One person's membership of a workspace, joined with enough of their profile to render a roster.
 *
 * <p>The profile half comes from the users module through its service, not from its table.
 */
@Schema(name = "WorkspaceMember")
public record MemberResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        @Schema(example = "ADMIN") String roleSlug,
        @Schema(example = "Admin") String roleName,
        @Schema(description = "Whether the account may currently sign in", example = "ACTIVE") String userStatus,
        Instant joinedAt) {}
