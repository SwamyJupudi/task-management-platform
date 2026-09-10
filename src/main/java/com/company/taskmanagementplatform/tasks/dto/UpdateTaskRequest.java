package com.company.taskmanagementplatform.tasks.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Edits a task. Every field is optional; an omitted one is left alone.
 *
 * <p>A blank description clears it. A labels list replaces the whole set, and an empty list clears
 * them, because labels are a set rather than a sequence of additions.
 *
 * <p>Status is absent on purpose: it has its own endpoint, because a transition is checked against
 * the state machine and a rejected one is a conflict rather than a validation error. Assignee is
 * absent for the same kind of reason, since changing it needs a different permission from editing
 * the rest.
 */
@Schema(name = "UpdateTaskRequest")
public record UpdateTaskRequest(
        @Size(min = 1, max = 200) @Schema(example = "Implement refresh token rotation") String title,
        @Size(max = 10000) String description,
        @Schema(example = "HIGH") String priority,
        LocalDate startDate,
        LocalDate dueDate,
        @Schema(description = "Send true to clear both dates") Boolean clearDates,
        @Min(0) Integer estimatedMinutes,
        @Min(0) Integer actualMinutes,
        @Min(0) @Schema(description = "Order within its board column") Integer boardPosition,
        @Schema(description = "Replaces every label on the task") List<String> labels) {}
