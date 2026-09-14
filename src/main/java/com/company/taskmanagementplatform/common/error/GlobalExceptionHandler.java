package com.company.taskmanagementplatform.common.error;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.company.taskmanagementplatform.common.error.ApiErrorResponse.FieldViolation;
import com.company.taskmanagementplatform.common.web.RequestIdFilter;

/**
 * Translates every exception into the one error body the API promises.
 *
 * <p>Two rules hold throughout. The client is told what it can act on and nothing more, so no
 * exception message, class name or stack trace from an unexpected failure ever reaches it. The
 * internal detail is not lost, it is logged against the same request id the client receives.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplication(ApplicationException ex, HttpServletRequest request) {
        return respond(ex.errorCode(), ex.getMessage(), request, ex, List.of());
    }

    /**
     * A rate limit, which is the one deliberate failure that carries a header as well as a body.
     *
     * <p>Declared separately from {@link #handleApplication} even though the exception extends {@code
     * ApplicationException} and that handler would match it: Spring picks the most specific handler, and
     * the body has to come out of {@code respond} so that a 429 is shaped exactly like every other error
     * and logged the same way. Only the header is added here.
     *
     * <p>{@code Retry-After} is not decoration. A 429 without it tells a client to back off without saying
     * how far, and the reasonable thing to do with that instruction is retry at once.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyRequests(
            TooManyRequestsException ex, HttpServletRequest request) {

        ResponseEntity<ApiErrorResponse> response = respond(ex.errorCode(), ex.getMessage(), request, ex, List.of());
        return ResponseEntity.status(response.getStatusCode())
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        return respond(ErrorCode.VALIDATION_ERROR, null, request, ex, violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_ERROR, null, request, ex, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(String.valueOf(v.getPropertyPath()), v.getMessage()))
                .toList();

        return respond(ErrorCode.VALIDATION_ERROR, null, request, ex, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, null, request, ex, List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.BAD_REQUEST, null, request, ex, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, null, request, ex, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, null, request, ex, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, request, ex, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(ErrorCode.PAYLOAD_TOO_LARGE, null, request, ex, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHORIZED, null, request, ex, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, null, request, ex, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        // A database message can name columns, constraints and values,
        // so it is logged and never returned to the caller.
        return respond(ErrorCode.CONFLICT, null, request, ex, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.INTERNAL_ERROR, null, request, ex, List.of());
    }

    private ResponseEntity<ApiErrorResponse> respond(
            ErrorCode code,
            String messageOverride,
            HttpServletRequest request,
            Exception ex,
            List<FieldViolation> violations) {

        String requestId = RequestIdFilter.currentRequestId(request);
        String path = request.getRequestURI();

        // A deliberate application error carries a message written for the user.
        // Everything else falls back to the safe default for the code.
        String message = messageOverride != null ? messageOverride : code.defaultMessage();

        if (code.status().is5xxServerError()) {
            log.error(
                    "Unhandled failure: status={} code={} method={} path={}",
                    code.status().value(),
                    code.name(),
                    request.getMethod(),
                    path,
                    ex);
        } else {
            log.warn(
                    "Request rejected: status={} code={} method={} path={} reason={}",
                    code.status().value(),
                    code.name(),
                    request.getMethod(),
                    path,
                    ex.getMessage());
        }

        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, message, path, requestId, violations));
    }
}
