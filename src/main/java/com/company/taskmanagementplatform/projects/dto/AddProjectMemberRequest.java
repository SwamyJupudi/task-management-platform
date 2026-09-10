package com.company.taskmanagementplatform.projects.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** Adds an existing workspace member to a project. */
@Schema(name = "AddProjectMemberRequest")
public record AddProjectMemberRequest(
        @NotNull @Schema(description = "Must already be a member of this workspace") UUID userId) {}
