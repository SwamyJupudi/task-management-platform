package com.company.taskmanagementplatform.tasks.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Both directions of one task's dependencies.
 *
 * <p>Returned together because they are one question. A client showing a task wants to know what it
 * is waiting for and what is waiting on it, and asking twice would double the round trips for no
 * gain.
 */
@Schema(name = "TaskDependencies")
public record TaskDependencyResponse(
        UUID taskId,
        @Schema(description = "Tasks this one waits on") List<TaskLinkResponse> blockedBy,
        @Schema(description = "Tasks waiting on this one") List<TaskLinkResponse> blocking) {}
