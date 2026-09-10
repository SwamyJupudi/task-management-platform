package com.company.taskmanagementplatform.workspaces.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** A role and the permission codes it grants. */
@Schema(name = "Role")
public record RoleResponse(
        UUID id,
        @Schema(example = "ADMIN") String slug,
        @Schema(example = "Admin") String name,
        @Schema(example = "WORKSPACE") String scope,
        @Schema(description = "Seeded roles cannot be deleted") boolean system,
        List<String> permissions) {}
