package com.company.taskmanagementplatform.common.error;

/** Raised for a request that is well formed but violates a business rule. */
public class BadRequestException extends ApplicationException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}
