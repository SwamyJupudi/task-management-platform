package com.company.taskmanagementplatform.workspaces.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An outstanding or settled invitation.
 *
 * <p>The token is absent, and not by oversight. It is generated once, sent to the recipient and
 * stored only as a hash, so there is nothing here to return even to an administrator.
 */
@Schema(name = "Invitation")
public record InvitationResponse(
        UUID id,
        UUID workspaceId,
        @Schema(example = "person@example.com") String email,
        @Schema(example = "EMPLOYEE") String roleSlug,
        @Schema(example = "PENDING") String status,
        Instant expiresAt,
        Instant createdAt) {}
