package com.company.taskmanagementplatform.activity.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One recorded action, as somebody reads it back.
 *
 * <p>{@code summary} is composed when the row is read, from the identifiers in it and the names they
 * resolve to now. Nothing of the sort is stored: a sentence written into the table would still say
 * "Ada Lovelace" a year after Ada changed her name, and the audit trail would be quietly wrong in a
 * way nobody would think to check.
 *
 * <p>{@code metadata} is returned as well as the sentence, because a client that wants to render its
 * own line, or to link to the thing that changed, should not have to parse English to do it.
 *
 * <p>The actor may be null. An action taken by the platform rather than by a person has no actor, and
 * so does one whose account has since been removed.
 */
@Schema(name = "ActivityEntry")
public record ActivityResponse(
        UUID id,
        UUID workspaceId,
        @Schema(description = "Who did it, or null for the platform itself") UUID actorUserId,
        @Schema(example = "ada@example.com") String actorEmail,
        @Schema(example = "Ada Lovelace") String actorName,
        @Schema(example = "task.status_changed") String action,
        @Schema(example = "TASK") String entityType,
        UUID entityId,
        @Schema(description = "The project this happened in, where there is one") UUID projectId,
        @Schema(description = "The facts of the action, differing by action")
                Map<String, Object> metadata,
        @Schema(description = "Composed on read, never stored", example = "Ada Lovelace moved it from TODO to IN PROGRESS")
                String summary,
        @Schema(description = "The correlation id of the request that caused it") String requestId,
        Instant createdAt) {}
