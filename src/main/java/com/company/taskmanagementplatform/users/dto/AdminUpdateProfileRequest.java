package com.company.taskmanagementplatform.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Changes an administrator may make to somebody else's profile.
 *
 * <p>The same two fields as {@link UpdateProfileRequest}, and the same bounds, but a separate type
 * rather than a reuse. The two requests will diverge the first time either side gains a field, and a
 * shared body would make that divergence a breaking change for whichever side did not want it.
 *
 * <p>The address is absent here for the same reason it is absent there, and one more besides: it is
 * the account's identity, changing it needs re-verification and a decision about live sessions, and
 * an administrator changing somebody's address without their knowledge is an account takeover with
 * extra steps.
 */
@Schema(name = "AdminUpdateProfileRequest")
public record AdminUpdateProfileRequest(
        @NotBlank @Size(max = 80) @Schema(example = "Ada") String firstName,
        @NotBlank @Size(max = 80) @Schema(example = "Lovelace") String lastName) {}
