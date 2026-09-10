package com.company.taskmanagementplatform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Creates an account.
 *
 * <p>The password bounds are checked again in the service against the configured policy. Declaring
 * them here as well gives the caller a field-level message instead of a single sentence, and stops an
 * unbounded string from ever reaching the hashing code.
 */
@Schema(name = "RegisterRequest")
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "person@example.com") String email,
        @NotBlank @Size(min = 8, max = 200) @Schema(description = "At least 8 characters") String password,
        @NotBlank @Size(max = 80) @Schema(example = "Ada") String firstName,
        @NotBlank @Size(max = 80) @Schema(example = "Lovelace") String lastName) {}
