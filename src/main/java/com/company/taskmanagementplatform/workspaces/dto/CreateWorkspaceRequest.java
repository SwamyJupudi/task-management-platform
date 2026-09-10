package com.company.taskmanagementplatform.workspaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Creates a workspace. Only a platform administrator may send this. */
@Schema(name = "CreateWorkspaceRequest")
public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Platform Team") String name,
        @NotBlank
                @Size(max = 60)
                @Pattern(
                        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                        message = "must be lowercase words separated by single hyphens")
                @Schema(example = "platform-team")
                String slug) {}
