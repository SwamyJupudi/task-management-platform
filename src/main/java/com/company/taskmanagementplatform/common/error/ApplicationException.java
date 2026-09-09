package com.company.taskmanagementplatform.common.error;

/**
 * Base type for every error the application raises deliberately.
 *
 * <p>Anything extending this is considered an expected outcome and is reported to the client with
 * its own code and message. Anything else is treated as a defect and reported as a generic internal
 * error.
 */
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ApplicationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
