package com.company.taskmanagementplatform.reports.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How a set of tasks is spread across the two things they are spread across.
 *
 * <p>Both breakdowns in one body because they are two views of one question and a client drawing a
 * dashboard needs both. Two endpoints would mean two round trips and, worse, two moments: a task
 * finished between them would be counted in one chart and not the other, and the pair would not add
 * up.
 *
 * <p>Every status and every priority is present, including those nothing holds. A chart with a
 * missing column is one a client has to know the full set to draw.
 *
 * @param byStatus the board columns, in board order
 * @param byPriority the four levels, lowest first
 * @param total how many tasks the breakdowns are of. Each list sums to it, which is what makes it
 *     possible to check a chart against itself
 */
@Schema(name = "Distribution", description = "Task counts by status and by priority")
public record DistributionResponse(
        List<CountByKeyResponse> byStatus,
        List<CountByKeyResponse> byPriority,
        @Schema(example = "137") long total) {}
