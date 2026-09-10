package com.company.taskmanagementplatform.teams.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One person's place in a team, joined with enough of their profile to render a roster.
 *
 * <p>The profile half comes from the users module through its service, not from its table, and the
 * lead flag is derived from the team rather than stored on the row: leadership is a property of the
 * team, and holding it in two places would let the two disagree.
 */
@Schema(name = "TeamMember")
public record TeamMemberResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        @Schema(description = "Whether this member is the team's lead") boolean lead,
        @Schema(example = "ACTIVE") String userStatus,
        Instant joinedAt) {}
