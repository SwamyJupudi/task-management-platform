package com.company.taskmanagementplatform.subtasks.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Edits one checklist item. Every field is optional; an omitted one is left alone.
 *
 * <p>The assignee is a field here rather than an endpoint of its own, unlike a task's. A subtask is
 * lighter, and assigning one needs no permission a task's edit does not already need.
 *
 * <p>Status is absent for the same reason it is absent from a task's edit: a transition is checked
 * against the state machine and has its own address.
 */
@Schema(name = "UpdateSubtaskRequest")
public record UpdateSubtaskRequest(
        @Size(min = 1, max = 200) @Schema(example = "Implement JWT") String title,
        @Schema(description = "Must already be a member of the parent task's project") UUID assigneeUserId,
        @Schema(description = "Send true to leave it unassigned") Boolean clearAssignee,
        LocalDate dueDate,
        @Schema(description = "Send true to clear the due date") Boolean clearDueDate,
        @Min(0) Integer position) {}
