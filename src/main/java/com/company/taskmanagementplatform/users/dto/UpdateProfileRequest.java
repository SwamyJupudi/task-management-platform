package com.company.taskmanagementplatform.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Changes a person may make to their own profile. The address is not among them. */
@Schema(name = "UpdateProfileRequest")
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) @Schema(example = "Ada") String firstName,
        @NotBlank @Size(max = 80) @Schema(example = "Lovelace") String lastName) {}
