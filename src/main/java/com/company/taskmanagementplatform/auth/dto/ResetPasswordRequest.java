package com.company.taskmanagementplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sets a new password using a token from a reset message. Ends every session. */
@Schema(name = "ResetPasswordRequest")
public record ResetPasswordRequest(
        @NotBlank String token, @NotBlank @Size(min = 8, max = 200) String newPassword) {}
