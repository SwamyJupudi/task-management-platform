package com.company.taskmanagementplatform.auth;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.ratelimit.AccountRateLimitGuard;
import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.users.UserAdminEvents;

/**
 * Registration and the verification of an address.
 *
 * <p>Registering creates a person and nothing else. It does not create a workspace and it grants no
 * access to one: that comes from an invitation or from an administrator adding a membership. The
 * account cannot sign in until the address is confirmed.
 */
@Service
public class RegistrationService {

    private final UserAccountService users;
    private final SingleUseTokenService tokens;
    private final ApplicationEventPublisher events;
    private final SecurityProperties.Tokens tokenProperties;
    private final AccountRateLimitGuard rateLimits;

    RegistrationService(
            UserAccountService users,
            SingleUseTokenService tokens,
            ApplicationEventPublisher events,
            SecurityProperties securityProperties,
            AccountRateLimitGuard rateLimits) {
        this.users = users;
        this.tokens = tokens;
        this.events = events;
        this.tokenProperties = securityProperties.tokens();
        this.rateLimits = rateLimits;
    }

    @Transactional
    public UserAccount register(String email, String password, String firstName, String lastName) {
        // BEFORE the existence check inside users.register, which is the only
        // ordering that closes anything. architecture.md accepted that this
        // endpoint answers 409 for an address that already has an account, and so
        // discloses that it is registered, on the stated condition that the
        // hardening phase limit registration by address as well as by caller.
        // Consumed here, an attacker gets a handful of answers about an address per
        // hour; consumed after the check, they would get as many as they liked and
        // the limit would only bound the successes.
        rateLimits.checkRegistration(email);

        UserAccount account = users.register(email, password, firstName, lastName);
        sendVerification(account.id(), account.email());
        return account;
    }

    @Transactional
    public void verify(String rawToken) {
        UUID userId = tokens.consume(rawToken, UserTokenType.EMAIL_VERIFICATION);
        users.markEmailVerified(userId);
    }

    /**
     * Sends another verification message, if there is anything to send.
     *
     * <p>Answers the same way whether or not the address is registered and whether or not it is
     * already confirmed. Anything else would turn this endpoint into a way of testing addresses.
     */
    @Transactional
    public void resendVerification(String email) {
        // Reachable without signing in and it sends mail to whatever address it is
        // given, so the limit is as much about not being usable to pester somebody
        // as it is about load. Before the lookup, so a refused caller learns nothing
        // about whether the address exists.
        rateLimits.checkRecovery(email);

        users.findByEmail(email)
                .filter(account -> !account.isEmailVerified())
                .ifPresent(account -> sendVerification(account.id(), account.email()));
    }

    /**
     * Sends another verification message on somebody else's behalf, for the admin panel.
     *
     * <p>Answers honestly where {@link #resendVerification} deliberately does not. That one is
     * reachable without signing in and must not become a way of testing addresses; this one is
     * reached only by a caller holding {@code user:update} on a platform role, who can already list
     * every account, so 404 for a missing account and 409 for one that is already verified are both
     * useful rather than disclosing.
     */
    @Transactional
    public void resendVerificationFor(UUID actorUserId, UUID userId) {
        UserAccount account = users.findById(userId)
                .orElseThrow(() -> com.company.taskmanagementplatform.common.error.ResourceNotFoundException.of(
                        "User", userId));

        if (account.isEmailVerified()) {
            throw new com.company.taskmanagementplatform.common.error.ConflictException(
                    "That address has already been verified.");
        }

        sendVerification(account.id(), account.email());
        events.publishEvent(new UserAdminEvents.VerificationResent(actorUserId, userId));
    }

    private void sendVerification(UUID userId, String email) {
        String rawToken =
                tokens.issue(userId, UserTokenType.EMAIL_VERIFICATION, tokenProperties.emailVerificationTtl());
        events.publishEvent(new IdentityMailEvents.VerificationRequested(email, rawToken));
    }
}
