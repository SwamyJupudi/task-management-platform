package com.company.taskmanagementplatform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An address alone, for the two endpoints that send something to it: forgotten password, and
 * resending a verification message.
 *
 * <p>Both always answer the same way whether or not the address is known, so this shape is shared and
 * so is the response.
 */
@Schema(name = "EmailRequest")
public record EmailRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "person@example.com") String email) {}
