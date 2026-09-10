package com.company.taskmanagementplatform.workspaces.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** A workspace, with the fields that exist in the identity phase. */
@Schema(name = "Workspace")
public record WorkspaceResponse(
        UUID id,
        @Schema(example = "Platform Team") String name,
        @Schema(example = "platform-team") String slug,
        @Schema(example = "ACTIVE") String status,
        Instant createdAt) {}
