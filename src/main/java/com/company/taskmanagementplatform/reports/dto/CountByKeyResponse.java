package com.company.taskmanagementplatform.reports.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One column of a breakdown: what it counts and how many.
 *
 * <p>A list of these rather than a map keyed by status, deliberately. A map has no order, and the
 * order of a status breakdown is the order of the board: TODO, then in progress, then review, then
 * done. A client that received an object would have to know the sequence to draw it, which means
 * knowing the platform's enum, which is exactly what an API is for not making it do.
 *
 * @param key the machine value, for a client that wants to link or filter by it
 * @param label the same value written for a person
 * @param count how many, never null and never absent: a status nothing holds is present at zero
 */
@Schema(name = "CountByKey", description = "One column of a breakdown")
public record CountByKeyResponse(
        @Schema(example = "IN_PROGRESS") String key,
        @Schema(example = "In progress") String label,
        @Schema(example = "12") long count) {}
