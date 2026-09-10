package com.company.taskmanagementplatform.subtasks.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Adds one item to a task's checklist.
 *
 * <p>A subtask carries no description. The requirements show subtasks as a list of titles under a
 * task, and a body field would be inventing a requirement rather than meeting one.
 */
@Schema(name = "CreateSubtaskRequest")
public record CreateSubtaskRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Implement JWT") String title,
        @Schema(description = "Must already be a member of the parent task's project") UUID assigneeUserId,
        LocalDate dueDate,
        @Min(0) @Schema(description = "Order in the checklist; defaults to the end") Integer position) {}
