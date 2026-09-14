package com.company.taskmanagementplatform.common.ratelimit;

import java.time.Duration;

/**
 * Whether one request may proceed, and how long the caller should wait if not.
 *
 * @param allowed whether the request is within its limit. <strong>True is also the answer when the
 *     store could not be reached</strong>, which is the fail-open decision recorded on {@link
 *     RedisRateLimiter}: a rate limiter is a protection, and a protection that becomes an outage when
 *     its own infrastructure is unavailable has turned one failure into two
 * @param retryAfter how long until the window resets. Only meaningful when {@code allowed} is false,
 *     and it is what the {@code Retry-After} header carries
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, Duration.ZERO);

    /**
     * Within the limit, or the store could not say otherwise.
     *
     * <p>Named {@code allow} rather than {@code allowed} because a record's component already owns that
     * name, and a static factory cannot share it.
     */
    public static RateLimitDecision allow() {
        return ALLOWED;
    }

    /**
     * Over the limit. Named for symmetry with {@link #allow()}.
     *
     * @param retryAfter the remaining life of the window. Floored at one second, because a {@code
     *     Retry-After: 0} invites an immediate retry that will also be refused
     */
    public static RateLimitDecision refuse(Duration retryAfter) {
        Duration wait = retryAfter == null || retryAfter.compareTo(Duration.ofSeconds(1)) < 0
                ? Duration.ofSeconds(1)
                : retryAfter;
        return new RateLimitDecision(false, wait);
    }

    /** The value the {@code Retry-After} header takes, rounded up so it is never short. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
