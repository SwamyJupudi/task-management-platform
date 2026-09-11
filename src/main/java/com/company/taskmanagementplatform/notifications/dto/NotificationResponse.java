package com.company.taskmanagementplatform.notifications.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One thing somebody was told, as they read it back.
 *
 * <p>{@code message} is composed when the row is read, never stored. {@code metadata} is returned
 * beside it because a client that wants to render its own line, or to link to the thing that
 * changed, should not have to parse English to do it.
 *
 * <p>{@code actorUserId} may be null: the deadline scan is performed by the platform rather than by a
 * person. {@code taskKey} may be null for a different reason, and the difference matters. It is
 * absent when the target has been removed, and absent when the recipient can no longer see the
 * project it lives in, which is how a notification stops naming work its recipient has lost access
 * to. The row stays in the feed either way; it simply stops offering a link.
 */
@Schema(name = "Notification")
public record NotificationResponse(
        UUID id,
        @Schema(example = "task.status_changed") String type,
        @Schema(description = "Composed on read, never stored", example = "Ada Lovelace moved Build login from todo to in progress.")
                String message,
        @Schema(description = "Who caused it, or null for the platform itself") UUID actorUserId,
        @Schema(example = "Ada Lovelace") String actorName,
        @Schema(example = "TASK", description = "PROJECT, TASK or COMMENT") String entityType,
        @Schema(description = "What to open: the task, comment or project this is about") UUID entityId,
        UUID projectId,
        @Schema(description = "The task's key, absent when it is gone or no longer visible", example = "PROJ-12")
                String taskKey,
        @Schema(description = "The facts of the notification, differing by type") Map<String, Object> metadata,
        @Schema(description = "When it was read, or null while it is unread") Instant readAt,
        Instant createdAt) {}
