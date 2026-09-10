package com.company.taskmanagementplatform.auth;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

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

    private void sendVerification(UUID userId, String email) {
        String rawToken =
                tokens.issue(userId, UserTokenType.EMAIL_VERIFICATION, tokenProperties.emailVerificationTtl());
        events.publishEvent(new IdentityMailEvents.VerificationRequested(email, rawToken));
    }
}
