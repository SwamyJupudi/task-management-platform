package com.company.taskmanagementplatform.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * The decisions the limiter makes from what Redis tells it, and what it does when Redis tells it
 * nothing.
 *
 * <p>The script itself is exercised against a real server in {@code RateLimitIT}; what is worth
 * isolating here is the branch that cannot be provoked reliably against a working store — the one where
 * the store is not working.
 */
class RedisRateLimiterTest {

    private static final RateLimitRule RULE = new RateLimitRule("test", 3, Duration.ofMinutes(1));

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @Test
    void allowsARequestInsideTheLimit() {
        givenRedisReturns(1L, 60_000L);

        assertThat(limiter().tryConsume("k", RULE).allowed()).isTrue();
    }

    @Test
    void allowsTheRequestThatExactlyReachesTheLimit() {
        // A limit of three means three are allowed, not two.
        givenRedisReturns(3L, 40_000L);

        assertThat(limiter().tryConsume("k", RULE).allowed()).isTrue();
    }

    @Test
    void refusesTheRequestAfterTheLimit() {
        givenRedisReturns(4L, 40_000L);

        assertThat(limiter().tryConsume("k", RULE).allowed()).isFalse();
    }

    @Test
    void takesTheWaitFromTheRemainingLifeOfTheWindow() {
        givenRedisReturns(9L, 12_400L);

        RateLimitDecision decision = limiter().tryConsume("k", RULE);

        assertThat(decision.retryAfter()).isEqualTo(Duration.ofMillis(12_400));
        // Rounded up, so the header never tells a client to retry before the window
        // has actually reset.
        assertThat(decision.retryAfterSeconds()).isEqualTo(13);
    }

    @Test
    void fallsBackToTheWholeWindowIfTheKeyHasNoExpiry() {
        // The script makes this impossible, because it sets the window in the same
        // atomic call that creates the key. If it ever happened, the window is the
        // only honest answer available.
        givenRedisReturns(9L, -1L);

        assertThat(limiter().tryConsume("k", RULE).retryAfter()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void allowsTheRequestWhenRedisCannotBeReached() {
        // The fail-open decision. A rate limiter is a protection, and one that
        // refuses everything when its own store is down has turned a degraded
        // dependency into a total outage.
        when(redis.execute(any(RedisScript.class), any(), any()))
                .thenThrow(new RedisConnectionFailureException("no route to host"));

        assertThat(limiter().tryConsume("k", RULE).allowed()).isTrue();
    }

    @Test
    void allowsTheRequestWhenRedisAnswersWithSomethingUnexpected() {
        when(redis.execute(any(RedisScript.class), any(), any())).thenReturn(List.of(1L));

        assertThat(limiter().tryConsume("k", RULE).allowed()).isTrue();
    }

    @Test
    void stopsAskingRedisForABackoffPeriodAfterAFailure() {
        // Without this the fail-open would be honoured and the application would
        // still be unusable: every request during an outage would pay the connect
        // timeout before being allowed through.
        when(redis.execute(any(RedisScript.class), any(), any()))
                .thenThrow(new RedisConnectionFailureException("no route to host"));

        RateLimiter limiter = new RedisRateLimiter(redis, Duration.ofSeconds(30));
        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryConsume("k", RULE).allowed()).isTrue();
        }

        verify(redis, times(1)).execute(any(RedisScript.class), any(), any());
    }

    @Test
    void triesAgainOnceTheBackoffHasElapsed() {
        when(redis.execute(any(RedisScript.class), any(), any()))
                .thenThrow(new RedisConnectionFailureException("no route to host"));

        // A backoff of zero means the next call is past it already, which is the
        // same code path a recovered Redis takes without the test having to wait.
        RateLimiter limiter = new RedisRateLimiter(redis, Duration.ZERO);
        limiter.tryConsume("k", RULE);
        limiter.tryConsume("k", RULE);

        verify(redis, times(2)).execute(any(RedisScript.class), any(), any());
    }

    @Test
    void passesTheWindowToRedisInMilliseconds() {
        givenRedisReturns(1L, 60_000L);

        limiter().tryConsume("k", new RateLimitRule("test", 3, Duration.ofMinutes(15)));

        verify(redis).execute(any(RedisScript.class), any(), org.mockito.ArgumentMatchers.eq("900000"));
    }

    @Test
    void neverConsultsRedisForAKeyItWasNotGiven() {
        givenRedisReturns(1L, 60_000L);

        limiter().tryConsume("rl:v1:test:ip:203.0.113.7", RULE);

        verify(redis).execute(any(RedisScript.class), org.mockito.ArgumentMatchers.argThat(keys -> keys != null
                        && keys.size() == 1
                        && "rl:v1:test:ip:203.0.113.7".equals(keys.get(0))), any());
        verify(redis, never()).delete(anyString());
    }

    private RateLimiter limiter() {
        return new RedisRateLimiter(redis, Duration.ofSeconds(30));
    }

    private void givenRedisReturns(long count, long ttlMillis) {
        when(redis.execute(any(RedisScript.class), any(), any())).thenReturn(List.of(count, ttlMillis));
    }
}
