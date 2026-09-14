package com.company.taskmanagementplatform.common.error;

import java.time.Duration;

/**
 * Raised when a caller has used up a rate limit.
 *
 * <p>Answers 429 in the platform's one error body, through {@code GlobalExceptionHandler} like every
 * other deliberate failure. {@link ErrorCode#TOO_MANY_REQUESTS} has existed since the foundation phase
 * with nothing raising it; this is what raises it.
 *
 * <p>It carries the wait, because a 429 without {@code Retry-After} tells a client to back off without
 * saying how far, and the reasonable thing for a client to do with that is retry immediately.
 *
 * <p>The message never says which limit was hit or how much of it is left. That is not tidiness: the
 * per-account limits are keyed by address, so a message distinguishing "you have made too many attempts
 * against this account" from "too many requests from here" would confirm whether an address is registered
 * to anybody willing to trip the limit. One message for every limit gives that away nowhere.
 */
public class TooManyRequestsException extends ApplicationException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.defaultMessage());
        this.retryAfter = retryAfter == null || retryAfter.isNegative() ? Duration.ofSeconds(1) : retryAfter;
    }

    /** The value for {@code Retry-After}, in seconds, rounded up so it is never short. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
