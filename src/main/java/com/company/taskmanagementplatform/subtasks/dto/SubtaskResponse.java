package com.company.taskmanagementplatform.subtasks.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One checklist item, with its assignee resolved so a list needs no second call per row.
 *
 * <p>{@code completed} is derived from the status rather than stored beside it. The requirements name
 * completion and status separately; they are one fact, and this is the shape that says so without
 * letting the two disagree.
 */
@Schema(name = "Subtask")
public record SubtaskResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID taskId,
        @Schema(example = "Implement JWT") String title,
        @Schema(description = "The assignee, or null if nobody holds it") UUID assigneeUserId,
        @Schema(example = "ada@example.com") String assigneeEmail,
        @Schema(example = "Ada Lovelace") String assigneeName,
        @Schema(example = "DONE") String status,
        @Schema(description = "True when the status is DONE") boolean completed,
        LocalDate dueDate,
        @Schema(description = "Order in the checklist", example = "0") int position,
        @Schema(description = "When it was finished, or null") Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {}
