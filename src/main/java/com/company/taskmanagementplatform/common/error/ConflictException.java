package com.company.taskmanagementplatform.common.error;

/** Raised when a request cannot be applied because it conflicts with existing state. */
public class ConflictException extends ApplicationException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
