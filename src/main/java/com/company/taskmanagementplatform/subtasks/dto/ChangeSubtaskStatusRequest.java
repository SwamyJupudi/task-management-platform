package com.company.taskmanagementplatform.subtasks.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Moves a subtask to another status. DONE is what "completed" means for a checklist item. */
@Schema(name = "ChangeSubtaskStatusRequest")
public record ChangeSubtaskStatusRequest(
        @NotBlank @Schema(example = "DONE", description = "TODO, IN_PROGRESS, REVIEW or DONE") String status) {}
