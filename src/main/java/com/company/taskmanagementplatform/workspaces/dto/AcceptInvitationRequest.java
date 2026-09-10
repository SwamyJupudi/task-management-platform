package com.company.taskmanagementplatform.workspaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Redeems an invitation.
 *
 * <p>Two shapes in one, because an invitation reaches two kinds of person. Somebody who already has
 * an account sends the token alone and must be signed in as the invited address. Somebody who does
 * not sends the token together with a password and a name, and the account is created and joined in
 * one step.
 */
@Schema(name = "AcceptInvitationRequest")
public record AcceptInvitationRequest(
        @NotBlank @Schema(description = "The token from the invitation link") String token,
        @Size(max = 200) @Schema(description = "Required only when no account exists yet") String password,
        @Size(max = 80) @Schema(description = "Required only when no account exists yet") String firstName,
        @Size(max = 80) @Schema(description = "Required only when no account exists yet") String lastName) {}
