package com.company.taskmanagementplatform.support;

import java.time.Duration;

import com.company.taskmanagementplatform.common.security.SecurityProperties;

/**
 * Ready-made settings for unit tests, so each one states only the value it is actually about.
 *
 * <p>The defaults match the shipped configuration, which matters: a test that passes against
 * invented settings proves less than one that passes against the real ones.
 */
public final class TestSecurityProperties {

    public static final String SECRET = "test-only-signing-key-thirty-two-bytes-minimum";
    public static final String ISSUER = "task-management-platform";
    public static final String AUDIENCE = "task-management-platform-api";

    private TestSecurityProperties() {}

    public static SecurityProperties defaults() {
        return withAccessTtl(Duration.ofMinutes(15));
    }

    public static SecurityProperties withAccessTtl(Duration accessTtl) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(ISSUER, AUDIENCE, SECRET, accessTtl),
                new SecurityProperties.Cookie("refresh_token", "/api/v1/auth", true, Duration.ofDays(14)),
                new SecurityProperties.Password(8, 72),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new SecurityProperties.Tokens(Duration.ofHours(24), Duration.ofHours(1)));
    }

    public static SecurityProperties withLockout(int maxAttempts, Duration duration) {
        SecurityProperties base = defaults();
        return new SecurityProperties(
                base.jwt(),
                base.cookie(),
                base.password(),
                new SecurityProperties.Lockout(maxAttempts, duration),
                base.tokens());
    }

    public static SecurityProperties withRefreshTtl(Duration ttl) {
        SecurityProperties base = defaults();
        return new SecurityProperties(
                base.jwt(),
                new SecurityProperties.Cookie(
                        base.cookie().name(), base.cookie().path(), base.cookie().secure(), ttl),
                base.password(),
                base.lockout(),
                base.tokens());
    }
}
