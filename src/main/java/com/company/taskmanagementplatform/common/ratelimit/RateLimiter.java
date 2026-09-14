package com.company.taskmanagementplatform.common.ratelimit;

/**
 * Counts one request against a key and says whether it may proceed.
 *
 * <p>A port, for the same reason {@code FileStore} and {@code MailSender} are ports: the store behind
 * it is an infrastructure choice, and the two callers — the filter that limits by address and the
 * guard that limits by account — should not know which one is wired. It also makes the tests able to
 * state a limiter's behaviour without a server.
 *
 * <p><strong>Implementations must fail open.</strong> If the store cannot be reached the answer is
 * {@code allowed}, never an exception and never a refusal. A rate limiter exists to keep the
 * application available under abuse; one that refuses every request when its own backing store is down
 * has turned a degraded dependency into a total outage, which is a worse failure than the one it was
 * protecting against. The decision is recorded in full on {@link RedisRateLimiter}.
 */
public interface RateLimiter {

    /**
     * Counts one request and decides.
     *
     * @param key what is being limited — an address, or the hash of an account's address. Never
     *     anything secret and never a plain email; {@link RateLimitKeys} builds these
     * @param rule how many are allowed in what window
     * @return whether this request may proceed, and how long to wait if not
     */
    RateLimitDecision tryConsume(String key, RateLimitRule rule);
}
