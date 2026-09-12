package com.company.taskmanagementplatform.auth;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    RegistrationService(
            UserAccountService users,
            SingleUseTokenService tokens,
            ApplicationEventPublisher events,
            SecurityProperties securityProperties) {
        this.users = users;
        this.tokens = tokens;
        this.events = events;
        this.tokenProperties = securityProperties.tokens();
    }

    @Transactional
    public UserAccount register(String email, String password, String firstName, String lastName) {
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
