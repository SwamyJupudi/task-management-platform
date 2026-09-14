package com.company.taskmanagementplatform.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * The rate limiter, counting in Redis.
 *
 * <p>Redis rather than memory, because the limit has to be the same limit on every instance. A counter
 * held in a process gives each instance its own allowance, so a limit of five becomes five times however
 * many instances are running, and the figure changes every time the deployment scales. A counter is also
 * the one piece of state in this application that is worthless the moment it is a second old, which is
 * what makes an in-memory store the right home for it rather than a table.
 *
 * <p><strong>One round trip, and it is atomic.</strong> The script increments the key, sets the window
 * on the increment that created it, and reads the remaining life back. Doing that as three commands
 * would be a race: two requests arriving together could both see a count of one, both set the expiry,
 * and — worse — an {@code INCR} followed by a separate {@code EXPIRE} that never ran would leave a key
 * with no expiry at all, which is a counter that never resets and an account locked out for ever. A Lua
 * script runs to completion on the server with nothing interleaved, so the key and its window are
 * created together or not at all.
 *
 * <p><strong>Redis unavailable means the request is allowed.</strong> This is the decision the phase was
 * given and it is the right way round: a rate limiter is a protection, and one that refuses every request
 * when its own store is unreachable has converted a degraded dependency into a total outage. What is lost
 * while Redis is down is the protection, not the service. It is recorded here rather than only in
 * {@code architecture.md} because it is the kind of decision a later reader would otherwise 'fix'.
 *
 * <p><strong>A failure suppresses further attempts for a short while, and that matters more than it
 * looks.</strong> Without it every request during a Redis outage would pay the full connect timeout
 * before being allowed, so a store that is merely unreachable would add its timeout to the latency of
 * every request in the application — the fail-open would be honoured and the service would still be
 * unusable. After a failure the limiter stops asking for {@code unavailableBackoff}, answers allowed
 * immediately, and then tries once more. The log says so once per backoff window rather than once per
 * request, because a log line per request during an outage is its own denial of service.
 */
@SuppressWarnings("rawtypes") // Spring's RedisScript needs the raw List type for a multi-value reply
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /**
     * Increment, set the window if this increment created the key, and report both.
     *
     * <p>{@code PEXPIRE} only on the first increment, so the window is fixed from the first request
     * rather than extended by every one after it. Refreshing it on each request would turn a window into
     * a sliding ban that a steady stream of refused requests could hold open indefinitely.
     */
    private static final String SCRIPT =
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;
    private final Duration unavailableBackoff;

    /** Epoch millis before which Redis is not consulted, because it just failed. */
    private final AtomicLong suppressedUntil = new AtomicLong();

    RedisRateLimiter(StringRedisTemplate redis, Duration unavailableBackoff) {
        this.redis = redis;
        this.unavailableBackoff = unavailableBackoff;
        DefaultRedisScript<List> compiled = new DefaultRedisScript<>();
        compiled.setScriptText(SCRIPT);
        compiled.setResultType(List.class);
        this.script = compiled;
    }

    @Override
    public RateLimitDecision tryConsume(String key, RateLimitRule rule) {
        long now = System.currentTimeMillis();
        if (now < suppressedUntil.get()) {
            return RateLimitDecision.allow();
        }

        try {
            List<?> result = redis.execute(
                    script, List.of(key), Long.toString(rule.window().toMillis()));
            return decide(result, rule);
        } catch (RuntimeException e) {
            return unavailable(rule, e);
        }
    }

    private RateLimitDecision decide(List<?> result, RateLimitRule rule) {
        if (result == null || result.size() < 2) {
            // The script always returns two values, so this is not a shape the
            // server produces. Allowing is the same choice as any other failure.
            return RateLimitDecision.allow();
        }

        long count = ((Number) result.get(0)).longValue();
        long remainingMillis = ((Number) result.get(1)).longValue();

        if (count <= rule.limit()) {
            return RateLimitDecision.allow();
        }

        // A negative TTL means the key exists without one, which the script makes
        // impossible; the window is the honest answer if it ever happened.
        Duration retryAfter =
                remainingMillis > 0 ? Duration.ofMillis(remainingMillis) : rule.window();
        return RateLimitDecision.refuse(retryAfter);
    }

    /**
     * Opens the gate, and stops asking for a while.
     *
     * <p>The key is deliberately absent from the log line: it carries an address, and there is nothing
     * actionable in knowing which request happened to be the one that found the store down.
     */
    private RateLimitDecision unavailable(RateLimitRule rule, RuntimeException cause) {
        long now = System.currentTimeMillis();
        long previous = suppressedUntil.getAndSet(now + unavailableBackoff.toMillis());

        // Once per backoff window rather than once per request. A log line per
        // request during an outage is its own denial of service, and the second
        // one says nothing the first did not.
        if (previous < now) {
            log.warn(
                    "Rate limiting is unavailable and requests are being allowed through: rule={} "
                            + "suppressingChecksFor={}",
                    rule.name(),
                    unavailableBackoff,
                    cause);
        }
        return RateLimitDecision.allow();
    }
}
