package com.company.taskmanagementplatform.workspaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One entry of the global permission catalog. */
@Schema(name = "Permission")
public record PermissionResponse(
        @Schema(example = "member:invite") String code,
        @Schema(example = "member") String resource,
        @Schema(example = "invite") String action,
        String description) {}
