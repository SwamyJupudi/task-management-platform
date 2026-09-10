package com.company.taskmanagementplatform.auth;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Changing a password from inside a session, and recovering one from outside.
 *
 * <p>The two differ in how much they revoke, and the difference is deliberate. A change ends the
 * caller's other sessions and leaves theirs alive, because they are present and clearly themselves. A
 * reset ends every session without exception, because somebody resetting a password may be recovering
 * an account that was taken, and the session to preserve might be the attacker's.
 */
@Service
public class PasswordService {

    private final UserAccountService users;
    private final SingleUseTokenService tokens;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;
    private final SecurityProperties.Tokens tokenProperties;

    PasswordService(
            UserAccountService users,
            SingleUseTokenService tokens,
            RefreshTokenService refreshTokens,
            ApplicationEventPublisher events,
            SecurityProperties securityProperties) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.tokenProperties = securityProperties.tokens();
    }

    /**
     * Starts a recovery, if the address belongs to anybody.
     *
     * <p>Answers identically either way. An endpoint that says whether an address is registered is a
     * way to enumerate the user base, and this one is reachable without signing in.
     */
    @Transactional
    public void requestReset(String email) {
        users.findByEmail(email).ifPresent(account -> {
            String rawToken =
                    tokens.issue(account.id(), UserTokenType.PASSWORD_RESET, tokenProperties.passwordResetTtl());
            events.publishEvent(new IdentityMailEvents.PasswordResetRequested(account.email(), rawToken));
        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        UUID userId = tokens.consume(rawToken, UserTokenType.PASSWORD_RESET);
        users.changePassword(userId, newPassword);
        refreshTokens.revokeAllForUser(userId, RevocationReason.PASSWORD_RESET);

        users.findById(userId)
                .ifPresent(account ->
                        events.publishEvent(new IdentityMailEvents.PasswordChanged(account.email())));
    }

    /**
     * Changes a password for somebody already signed in.
     *
     * <p>Every session is revoked, this one included, and the caller is issued a fresh pair by the
     * controller. Keeping the current refresh token alive would have meant treating one token as
     * special, and the simpler rule leaves nothing to reason about.
     *
     * @throws UnauthorizedException if the current password is wrong
     */
    @Transactional
    public void change(UUID userId, String currentPassword, String newPassword) {
        if (!users.matchesCurrentPassword(userId, currentPassword)) {
            throw UnauthorizedException.invalidCredentials();
        }

        users.changePassword(userId, newPassword);
        refreshTokens.revokeAllForUser(userId, RevocationReason.PASSWORD_CHANGED);

        users.findById(userId)
                .map(UserAccount::email)
                .ifPresent(email -> events.publishEvent(new IdentityMailEvents.PasswordChanged(email)));
    }
}
