package com.company.taskmanagementplatform.common.error;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The single error body shape returned by every endpoint in the platform.
 *
 * <p>{@code requestId} is the value also written to every log line for the same request, so a user
 * reporting a failure gives support a key that finds the internal detail.
 */
@Schema(name = "ApiError", description = "Standard error response returned by every endpoint")
public record ApiErrorResponse(
        @Schema(description = "When the error was produced") OffsetDateTime timestamp,
        @Schema(description = "HTTP status code", example = "400") int status,
        @Schema(description = "Stable machine-readable code", example = "VALIDATION_ERROR") String code,
        @Schema(description = "Safe message suitable for display to the user") String message,
        @Schema(description = "Request path that produced the error") String path,
        @Schema(description = "Correlates this response with the server logs") String requestId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
                @Schema(description = "Per-field problems, present only for validation failures")
                List<FieldViolation> errors) {

    public static ApiErrorResponse of(ErrorCode code, String message, String path, String requestId) {
        return new ApiErrorResponse(
                OffsetDateTime.now(), code.status().value(), code.name(), message, path, requestId, List.of());
    }

    public static ApiErrorResponse of(
            ErrorCode code, String message, String path, String requestId, List<FieldViolation> errors) {
        return new ApiErrorResponse(
                OffsetDateTime.now(), code.status().value(), code.name(), message, path, requestId, errors);
    }

    @Schema(name = "FieldViolation", description = "A single rejected input value")
    public record FieldViolation(
            @Schema(example = "email") String field, @Schema(example = "must be a valid email address") String message) {}
}
