package com.company.taskmanagementplatform.projects.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Creates a project.
 *
 * <p>A named owner is added to the project as a member as well. An owner outside their own project
 * would be a state nothing else in the model expects, and it is what makes the rule that you cannot
 * remove the owner from the roster coherent.
 *
 * <p>A new project always starts in PLANNING. Letting the caller choose the opening status would
 * make the transition rules optional, since anything unreachable could be reached by creating a
 * project already in it.
 */
@Schema(name = "CreateProjectRequest")
public record CreateProjectRequest(
        @NotBlank
                @Size(max = 10)
                @Pattern(
                        regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$",
                        message = "must be 2 to 10 letters and digits, starting with a letter")
                @Schema(example = "PLAT", description = "Short handle, unique in the workspace")
                String key,
        @NotBlank @Size(max = 120) @Schema(example = "Platform Rebuild") String name,
        @Size(max = 2000) String description,
        @Schema(description = "Must already be a member of this workspace") UUID ownerUserId,
        @Schema(description = "Must be a team of this workspace") UUID teamId,
        @Schema(example = "HIGH", description = "LOW, MEDIUM, HIGH or CRITICAL; defaults to MEDIUM")
                String priority,
        LocalDate startDate,
        LocalDate endDate,
        @Schema(description = "Tags; unknown ones are added to the workspace catalog") List<String> labels) {}
