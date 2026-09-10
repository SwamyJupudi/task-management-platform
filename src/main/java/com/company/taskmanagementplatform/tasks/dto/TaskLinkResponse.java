package com.company.taskmanagementplatform.tasks.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One end of a dependency, named the way somebody would read it out.
 *
 * <p>Enough to render the link without a second request, and no more: a full task on each edge would
 * turn a page of twenty into a page of a hundred.
 */
@Schema(name = "TaskLink")
public record TaskLinkResponse(
        UUID id,
        @Schema(example = "12") int taskNumber,
        @Schema(example = "PLAT-12") String key,
        @Schema(example = "Implement refresh token rotation") String title,
        @Schema(example = "IN_PROGRESS") String status) {}
