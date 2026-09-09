package com.company.taskmanagementplatform.common.error;

import org.springframework.http.HttpStatus;

/**
 * The closed set of error codes the API may return.
 *
 * <p>Each code carries the HTTP status it maps to and a safe, user-facing message. The message is
 * deliberately free of internal detail: causes, stack traces and identifiers stay in the logs.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Some of the submitted values are not valid. Please review and try again."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read. Please check the request and try again."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be processed. Please check the request and try again."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "You need to sign in to perform this action."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested item could not be found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This action is not supported for that address."),
    CONFLICT(HttpStatus.CONFLICT, "This action conflicts with the current state of the data."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That content type is not supported."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded content is larger than the allowed limit."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait a moment and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong while processing your request. Please try again."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable. Please try again shortly.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
