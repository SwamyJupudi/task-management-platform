package com.company.taskmanagementplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Changes a password from inside a session.
 *
 * <p>The current password is required even though the caller is already authenticated, because an
 * unattended browser is the exact case this defends against.
 */
@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(min = 8, max = 200) String newPassword) {}
