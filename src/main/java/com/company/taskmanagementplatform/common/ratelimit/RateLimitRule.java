package com.company.taskmanagementplatform.common.ratelimit;

import java.time.Duration;

/**
 * How many requests a key may make in a window, and what to call the limit when it is hit.
 *
 * <p>A fixed window rather than a sliding one or a token bucket. The difference matters at the
 * boundary: a fixed window lets a caller spend its whole allowance at the end of one window and again
 * at the start of the next, so the true worst case over a short span is twice the limit. That is
 * accepted here, and the reason is that every limit below is set to catch abuse rather than to shape
 * traffic precisely — five registrations an hour is not a figure where eleven across a boundary
 * changes the outcome. What a fixed window buys is that the whole decision is one atomic {@code INCR}
 * against one key, which is cheap, obviously correct under concurrency, and leaves nothing to expire
 * but the key itself. A sliding window needs a sorted set per key and a trim on every request.
 *
 * @param name the rule's name, used in the key and in the log line. Part of the key, so changing it
 *     resets every counter using it
 * @param limit how many requests are allowed in one window
 * @param window how long the window lasts
 */
public record RateLimitRule(String name, int limit, Duration window) {

    public RateLimitRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A rate limit rule needs a name");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("A rate limit of " + limit + " would refuse everything");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("A rate limit needs a positive window");
        }
    }
}
