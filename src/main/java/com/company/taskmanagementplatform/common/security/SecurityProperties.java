package com.company.taskmanagementplatform.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable of the identity phase, in one place and supplied by the environment.
 *
 * <p>No value here has a hardcoded production default. The signing secret in particular is required,
 * so an environment that forgets it fails at startup instead of running on something guessable.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Jwt jwt, Cookie cookie, Password password, Lockout lockout, Tokens tokens) {

    /**
     * @param issuer written into and checked on every access token
     * @param audience likewise, so a token minted for another service is refused here
     * @param secret the signing key; at least 32 bytes, checked at startup
     * @param accessTtl how long an access token lives
     */
    public record Jwt(String issuer, String audience, String secret, Duration accessTtl) {}

    /**
     * @param name the refresh cookie's name
     * @param path scoped to the authentication endpoints, so it is not attached to ordinary calls
     * @param secure false only for plain-HTTP local development
     * @param ttl how long a refresh token lives, and the cookie with it
     */
    public record Cookie(String name, String path, boolean secure, Duration ttl) {}

    /**
     * @param minLength the shortest password accepted
     * @param maxBytes the longest; BCrypt ignores input past 72 bytes, so without this cap two
     *     different passwords could open the same account
     */
    public record Password(int minLength, int maxBytes) {}

    /**
     * @param maxAttempts consecutive failures before the account locks
     * @param duration how long the lock lasts
     */
    public record Lockout(int maxAttempts, Duration duration) {}

    /**
     * @param emailVerificationTtl lifetime of a verification token
     * @param passwordResetTtl lifetime of a reset token, shorter because it is more dangerous
     */
    public record Tokens(Duration emailVerificationTtl, Duration passwordResetTtl) {}
}
