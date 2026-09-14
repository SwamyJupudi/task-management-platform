package com.company.taskmanagementplatform.common.ratelimit;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.error.TooManyRequestsException;

/**
 * The limits keyed to an account rather than to an address.
 *
 * <p>Called from the services, not from a filter, and the reason is the request body. The address being
 * limited arrives inside it, and reading a body in a filter consumes the stream — every request in the
 * application would then need a caching wrapper so the controller could read it again, multipart uploads
 * included, which is a permanent cost on everything to avoid passing one string to a collaborator. By the
 * time a service has the address it has been parsed and validated already, so the check costs a method
 * call.
 *
 * <p><strong>Each check happens before the work it is protecting, and before the answer that would leak
 * anything.</strong> That ordering is the whole value of the registration limit: {@code architecture.md}
 * accepted that registering with an address that already has an account answers 409, and therefore
 * discloses that the address is registered, on the explicit condition that the hardening phase limit
 * registration by address as well as by caller. The limit only closes that hole if it is consumed before
 * the existence check, so an attacker gets three answers about an address per hour and not three thousand.
 * The same ordering matters for sign-in: the limit is counted before the credential is verified, so it
 * bounds the bcrypt comparisons rather than merely the replies.
 *
 * <p><strong>Refusing says nothing about which limit was hit.</strong> One message, from {@code
 * ErrorCode.TOO_MANY_REQUESTS}, for every rule. A message that distinguished "too many attempts against
 * this account" from "too many requests from here" would confirm an address is registered to anybody
 * prepared to trip the limit, which would give back exactly what the limit is here to protect.
 *
 * <p>Nothing here logs the address. {@link RateLimitKeys} hashes it before it reaches the store for the
 * same reason.
 */
@Component
public class AccountRateLimitGuard {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    AccountRateLimitGuard(RateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    /**
     * A sign-in attempt against one account.
     *
     * <p>Looser than the account lockout on purpose, so the lockout still fires first and a person who has
     * mistyped their password is told their account is locked rather than told to come back later. {@link
     * RateLimitProperties} sets out that reasoning in full.
     */
    public void checkLogin(String email) {
        check(properties.loginByAccount(), email);
    }

    /** A registration attempt naming one address. The enumeration limit; consume it before the 409. */
    public void checkRegistration(String email) {
        check(properties.registrationByAccount(), email);
    }

    /**
     * A password reset or a repeated verification message for one address.
     *
     * <p>Both send mail to somebody who may not have asked for it, so this limit is as much about the
     * platform not being usable to pester a person as it is about the platform's own load.
     */
    public void checkRecovery(String email) {
        check(properties.recoveryByAccount(), email);
    }

    private void check(RateLimitRule rule, String email) {
        if (!properties.enabled()) {
            return;
        }

        RateLimitDecision decision = limiter.tryConsume(RateLimitKeys.forEmail(rule.name(), email), rule);
        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfter());
        }
    }
}
