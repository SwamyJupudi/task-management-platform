package com.company.taskmanagementplatform.common.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every rate limit, and the one switch that turns them all off.
 *
 * <p>The figures are chosen to catch abuse, not to shape traffic. A person using the application never
 * approaches any of them; a script enumerating addresses or guessing passwords reaches them in seconds.
 * Setting them tighter would start refusing legitimate bursts — a page that fires six requests as it
 * loads, a client retrying after a flaky connection — for no gain against an attacker who is happy to go
 * slower.
 *
 * <p><strong>The account limits are deliberately looser than the account lockout.</strong> That looks
 * backwards and is the important detail in this file. Signing in wrongly five times locks the account for
 * fifteen minutes, and that lockout is the platform's answer to password guessing; it tells the caller
 * plainly that the account is locked, which is what somebody who has forgotten their password needs to
 * hear. If the per-account rate limit were five as well, the tenth attempt would be answered with a 429
 * before the lockout was ever reached, and a real person mistyping their password would be told to try
 * again later rather than that their account is locked. So the per-account login limit sits at twice the
 * lockout threshold: the lockout always fires first and keeps its behaviour, and this limit only catches
 * the case the lockout cannot — somebody working through many accounts, or continuing to hammer one that
 * is already locked.
 *
 * @param enabled whether any limit applies. On by default. Off is for a deployment that puts its limits
 *     in front of the application instead, at a gateway, which is the other reasonable place for them
 * @param unavailableBackoff how long the limiter stops consulting Redis after a failure. Not a tuning
 *     knob so much as the thing that makes failing open actually fast: without it every request during an
 *     outage would pay the connect timeout before being allowed through
 * @param requestsPerMinute the ceiling on one address across the whole API. Generous, because this is the
 *     backstop that keeps one caller from saturating the container's threads, not the limit that protects
 *     any particular endpoint
 * @param authRequestsPerMinute the ceiling on one address across the unauthenticated authentication
 *     endpoints. Much tighter, because these are the ones worth attacking and none of them is called
 *     repeatedly by a legitimate client
 * @param registrationsPerHour the ceiling on new accounts from one address. {@code architecture.md}
 *     accepted that registration discloses whether an address is already registered, on the stated
 *     condition that the hardening phase limit registration by address as well as by caller. This is the
 *     by-address half
 * @param loginAttemptsPerAccount the ceiling on sign-in attempts against one account, from anywhere, in a
 *     fifteen-minute window. Twice the lockout threshold, for the reason above
 * @param registrationsPerAccount the ceiling on registration attempts naming one address, from anywhere,
 *     in an hour. The by-caller half of the enumeration condition: without it, an attacker with a pool of
 *     addresses could test one account from many sources
 * @param recoveryRequestsPerAccount the ceiling, per hour, on reset and resend requests naming one
 *     address. Both send mail to somebody who did not ask for it, so this limit is as much about the
 *     platform not being usable to pester a person as it is about the platform's own load
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30s") Duration unavailableBackoff,
        @DefaultValue("300") int requestsPerMinute,
        @DefaultValue("20") int authRequestsPerMinute,
        @DefaultValue("5") int registrationsPerHour,
        @DefaultValue("10") int loginAttemptsPerAccount,
        @DefaultValue("3") int registrationsPerAccount,
        @DefaultValue("5") int recoveryRequestsPerAccount) {

    /** Everything, from one address. The backstop. */
    public RateLimitRule global() {
        return new RateLimitRule("global", requestsPerMinute, Duration.ofMinutes(1));
    }

    /** The unauthenticated authentication endpoints, from one address. */
    public RateLimitRule auth() {
        return new RateLimitRule("auth", authRequestsPerMinute, Duration.ofMinutes(1));
    }

    /** Registration, from one address. */
    public RateLimitRule registration() {
        return new RateLimitRule("register-ip", registrationsPerHour, Duration.ofHours(1));
    }

    /** Sign-in attempts against one account. Looser than the lockout, on purpose. */
    public RateLimitRule loginByAccount() {
        return new RateLimitRule("login-email", loginAttemptsPerAccount, Duration.ofMinutes(15));
    }

    /** Registration attempts naming one address. The enumeration limit. */
    public RateLimitRule registrationByAccount() {
        return new RateLimitRule("register-email", registrationsPerAccount, Duration.ofHours(1));
    }

    /** Reset and resend-verification requests naming one address. */
    public RateLimitRule recoveryByAccount() {
        return new RateLimitRule("recovery-email", recoveryRequestsPerAccount, Duration.ofHours(1));
    }
}
