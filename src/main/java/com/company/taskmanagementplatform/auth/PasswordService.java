package com.company.taskmanagementplatform.auth;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.ratelimit.AccountRateLimitGuard;
import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.users.UserAdminEvents;

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
    private final AccountRateLimitGuard rateLimits;

    PasswordService(
            UserAccountService users,
            SingleUseTokenService tokens,
            RefreshTokenService refreshTokens,
            ApplicationEventPublisher events,
            SecurityProperties securityProperties,
            AccountRateLimitGuard rateLimits) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.tokenProperties = securityProperties.tokens();
        this.rateLimits = rateLimits;
    }

    /**
     * Starts a recovery, if the address belongs to anybody.
     *
     * <p>Answers identically either way. An endpoint that says whether an address is registered is a
     * way to enumerate the user base, and this one is reachable without signing in.
     */
    @Transactional
    public void requestReset(String email) {
        // Before the lookup. This endpoint answers the same way whether or not the
        // address is registered, and a limit consumed after the lookup would only
        // count the addresses that exist, which would make the timing of the two
        // paths differ and give back what the uniform answer protects.
        rateLimits.checkRecovery(email);

        users.findByEmail(email).ifPresent(account -> {
            String rawToken =
                    tokens.issue(account.id(), UserTokenType.PASSWORD_RESET, tokenProperties.passwordResetTtl());
            events.publishEvent(new IdentityMailEvents.PasswordResetRequested(account.email(), rawToken));
        });
    }

    /**
     * Starts a recovery on somebody else's behalf, for the admin panel.
     *
     * <p>The same flow, entered from a different door. The token is issued to the account and mailed
     * to <strong>its own address</strong>, so the administrator learns nothing and the person who
     * owns the address is the only one who can complete it. Redeeming it revokes every session, as
     * any reset does.
     *
     * <p><strong>There is deliberately no endpoint that sets somebody's password.</strong> One would
     * put a raw password in an administrator's request body, and it would mean an administrator
     * knowing a credential that opens an account that is not theirs. This is what the requirements'
     * "account management" is served by instead.
     *
     * <p>Unlike {@link #requestReset}, this answers 404 for an account that does not exist. That one
     * is reachable without signing in and must not become a way to test whether an address is
     * registered; this one is reached only by a caller who already holds {@code user:update} on a
     * platform role and can list every account anyway, so an honest answer costs nothing.
     */
    @Transactional
    public void requestResetFor(UUID actorUserId, UUID userId) {
        UserAccount account = users.findById(userId)
                .orElseThrow(() -> com.company.taskmanagementplatform.common.error.ResourceNotFoundException.of(
                        "User", userId));

        String rawToken = tokens.issue(account.id(), UserTokenType.PASSWORD_RESET, tokenProperties.passwordResetTtl());
        events.publishEvent(new IdentityMailEvents.PasswordResetRequested(account.email(), rawToken));
        events.publishEvent(new UserAdminEvents.PasswordResetRequested(actorUserId, userId));
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
