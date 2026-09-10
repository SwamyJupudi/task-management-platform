package com.company.taskmanagementplatform.common.error;

/**
 * Raised when the caller is known but may not do what they asked.
 *
 * <p>Used for the account states that block a session even though the password was correct.
 * Authorization denials are raised by Spring Security as {@code AccessDeniedException} and handled
 * separately; they are not routed through here.
 */
public class ForbiddenException extends ApplicationException {

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ForbiddenException emailNotVerified() {
        return new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED, ErrorCode.EMAIL_NOT_VERIFIED.defaultMessage());
    }

    public static ForbiddenException accountInactive() {
        return new ForbiddenException(ErrorCode.ACCOUNT_INACTIVE, ErrorCode.ACCOUNT_INACTIVE.defaultMessage());
    }

    public static ForbiddenException accountLocked() {
        return new ForbiddenException(ErrorCode.ACCOUNT_LOCKED, ErrorCode.ACCOUNT_LOCKED.defaultMessage());
    }
}
