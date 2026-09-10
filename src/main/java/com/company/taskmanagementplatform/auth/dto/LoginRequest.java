package com.company.taskmanagementplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Signs in.
 *
 * <p>The address is not validated as an email here on purpose. A malformed one has no account, and
 * the answer must be the same generic rejection either way rather than a validation error that says
 * the address was at least well formed.
 */
@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank @Size(max = 254) @Schema(example = "person@example.com") String email,
        @NotBlank @Size(max = 200) String password) {}
