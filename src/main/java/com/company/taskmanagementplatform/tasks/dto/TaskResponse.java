package com.company.taskmanagementplatform.tasks.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A task, with the people and the project resolved so a listing needs no second call per row.
 *
 * <p>{@code key} is composed rather than stored: the project's key and the task's number, which is
 * how everybody refers to a task in conversation. The number alone is meaningless outside its
 * project, and a stored copy of the rendered form would be a second thing to keep in step.
 *
 * <p>The assignee fields are null when nobody holds the task, which is an ordinary state rather than
 * an error. So are the reporter fields, once the person who raised it has left the workspace.
 *
 * <p>Subtasks are deliberately not counted here. They are a module of their own and are fetched from
 * their own address; reading their table to put a "4 of 6" on this record would make the two modules
 * depend on each other in both directions, which nothing else in this platform does.
 */
@Schema(name = "Task")
public record TaskResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        @Schema(example = "PLAT") String projectKey,
        @Schema(example = "Platform Rebuild") String projectName,
        @Schema(example = "12") int taskNumber,
        @Schema(example = "PLAT-12", description = "The project key and the task number") String key,
        @Schema(example = "Implement refresh token rotation") String title,
        String description,
        @Schema(description = "The assignee, or null if nobody holds it") UUID assigneeUserId,
        @Schema(example = "ada@example.com") String assigneeEmail,
        @Schema(example = "Ada Lovelace") String assigneeName,
        @Schema(description = "Who raised it, or null if they have since left") UUID reporterUserId,
        @Schema(example = "grace@example.com") String reporterEmail,
        @Schema(example = "Grace Hopper") String reporterName,
        @Schema(example = "IN_PROGRESS") String status,
        @Schema(example = "HIGH") String priority,
        LocalDate startDate,
        LocalDate dueDate,
        @Schema(example = "480") Integer estimatedMinutes,
        @Schema(example = "520") Integer actualMinutes,
        @Schema(description = "Order within its board column", example = "0") int boardPosition,
        @Schema(description = "Labels on this task") List<String> labels,
        @Schema(description = "Tasks this one waits on") List<TaskLinkResponse> blockedBy,
        @Schema(description = "Tasks waiting on this one") List<TaskLinkResponse> blocking,
        @Schema(description = "True while an unfinished task blocks it") boolean blocked,
        @Schema(description = "When it was finished, or null") Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {}
