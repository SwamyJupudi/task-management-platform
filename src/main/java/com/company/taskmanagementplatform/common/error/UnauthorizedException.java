package com.company.taskmanagementplatform.common.error;

/**
 * Raised when the caller has not proved who they are, or the proof they offered is no longer good.
 *
 * <p>The code is supplied by the caller because the identity phase distinguishes several
 * unauthenticated outcomes, but every message they carry stays deliberately vague about which half
 * of a credential was wrong.
 */
public class UnauthorizedException extends ApplicationException {

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.defaultMessage());
    }

    public static UnauthorizedException tokenInvalid() {
        return new UnauthorizedException(ErrorCode.TOKEN_INVALID, ErrorCode.TOKEN_INVALID.defaultMessage());
    }

    public static UnauthorizedException tokenExpired() {
        return new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, ErrorCode.TOKEN_EXPIRED.defaultMessage());
    }
}
