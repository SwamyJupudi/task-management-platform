package com.company.taskmanagementplatform.common.error;

/**
 * Raised when something the caller sent is larger than the application allows.
 *
 * <p>The servlet container has its own limit and produces {@code MaxUploadSizeExceededException},
 * which the global handler already maps to the same code. This is the second line: the container
 * refuses what it can before reading a body, and the application refuses what it can only know once
 * it has. Both answer 413, so a client sees one behaviour and not two.
 */
public class PayloadTooLargeException extends ApplicationException {

    public PayloadTooLargeException(String message) {
        super(ErrorCode.PAYLOAD_TOO_LARGE, message);
    }
}
