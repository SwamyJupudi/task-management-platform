package com.company.taskmanagementplatform.tasks.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Raises a task in a project.
 *
 * <p>The project comes from the path rather than the body, because it is what the task is numbered
 * against and moving a task between projects is not something this phase supports.
 *
 * <p>A new task always starts in TODO. Letting the caller choose the opening status would make the
 * transition rules optional, since anything unreachable could be reached by creating a task already
 * in it.
 *
 * <p>The reporter defaults to whoever is asking, which is what "reporter" means in every tool this
 * one is compared to. Naming somebody else requires them to be a member of the workspace.
 */
@Schema(name = "CreateTaskRequest")
public record CreateTaskRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Implement refresh token rotation") String title,
        @Size(max = 10000) String description,
        @Schema(description = "Must already be a member of this project") UUID assigneeUserId,
        @Schema(description = "Must be a member of this workspace; defaults to the caller") UUID reporterUserId,
        @Schema(example = "HIGH", description = "LOW, MEDIUM, HIGH or CRITICAL; defaults to MEDIUM") String priority,
        LocalDate startDate,
        LocalDate dueDate,
        @Min(0) @Schema(example = "480", description = "Estimated effort in minutes") Integer estimatedMinutes,
        @Min(0) @Schema(example = "520", description = "Effort spent so far, in minutes") Integer actualMinutes,
        @Schema(description = "Labels; unknown ones are added to the workspace catalog") List<String> labels) {}
