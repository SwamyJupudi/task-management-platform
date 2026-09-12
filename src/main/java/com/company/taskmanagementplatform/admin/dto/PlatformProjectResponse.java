package com.company.taskmanagementplatform.admin.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One project on the cross-workspace overview.
 *
 * <p>The project as its own module describes it, plus the name of the workspace it belongs to. That
 * name is the whole reason this is a different shape from the ordinary project response: inside a
 * workspace it would be noise on every row, and here it is the column that makes the listing
 * readable at all.
 *
 * <p>The name is resolved through one bulk lookup for the page rather than one query per row, which
 * is how every listing in this platform is written.
 *
 * @param progress the derived column, read and never recomputed. One rule for it, in the module that
 *     owns it
 * @param workspaceName null only if the workspace was removed between the two reads of one
 *     transaction, which cannot happen inside a read-only transaction and is defended against
 *     anyway rather than thrown on
 */
@Schema(name = "PlatformProject", description = "A project, with the workspace it belongs to")
public record PlatformProjectResponse(
        UUID id,
        UUID workspaceId,
        String workspaceName,
        @Schema(example = "PROJ") String key,
        String name,
        @Schema(example = "ACTIVE") String status,
        UUID ownerUserId,
        UUID teamId,
        @Schema(description = "Derived from its tasks, 0-100", example = "62") int progress,
        Instant updatedAt) {}
