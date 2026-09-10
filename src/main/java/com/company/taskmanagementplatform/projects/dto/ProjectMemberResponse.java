package com.company.taskmanagementplatform.projects.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One person's place on a project, joined with enough of their profile to render a roster.
 *
 * <p>The owner flag is derived from the project rather than stored on the row: ownership is a
 * property of the project, and holding it in two places would let the two disagree.
 */
@Schema(name = "ProjectMember")
public record ProjectMemberResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        @Schema(description = "Whether this member owns the project") boolean owner,
        @Schema(example = "ACTIVE") String userStatus,
        Instant joinedAt) {}
