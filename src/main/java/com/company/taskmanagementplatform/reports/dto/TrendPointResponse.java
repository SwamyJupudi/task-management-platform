package com.company.taskmanagementplatform.reports.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One point on the productivity chart: what was taken on and what was finished.
 *
 * <p>Both lines, because either alone says nothing. A team completing forty tasks a week is keeping
 * up or falling behind depending entirely on how many arrived, and a chart showing only one of them
 * invites the wrong conclusion confidently.
 *
 * <p>Every bucket in the window is returned, including empty ones, so a quiet fortnight draws a flat
 * line rather than a gap the client has to interpolate across.
 *
 * <p>{@code bucketStart} is a local date in the workspace's timezone: the day itself, the Monday of
 * the week, or the first of the month.
 */
@Schema(name = "TrendPoint", description = "One bucket of the productivity trend")
public record TrendPointResponse(
        @Schema(description = "The day, the Monday, or the first of the month") LocalDate bucketStart,
        @Schema(example = "18") long created,
        @Schema(example = "15") long completed) {}
