package com.company.taskmanagementplatform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.users.UserDeactivatedEvent;

/**
 * Ends every session of an account that has just been switched off or removed.
 *
 * <p>Note the plain listener rather than an after-commit one. This runs inside the transaction that
 * deactivated the account, so the two either both happen or neither does. Deferring it until after
 * commit would leave a window in which the account is off and its sessions are alive, and a
 * deactivation that does not take effect is worse than one that fails loudly.
 *
 * <p>The access token is a separate matter and needs nothing here. The authentication filter reads
 * the account's state on every request, so it stops being honoured at the same moment.
 */
@Component
class SessionRevocationListener {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationListener.class);

    private final RefreshTokenService refreshTokens;

    SessionRevocationListener(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @EventListener
    void onUserDeactivated(UserDeactivatedEvent event) {
        int revoked = refreshTokens.revokeAllForUser(event.userId(), RevocationReason.ACCOUNT_DEACTIVATED);
        log.info("Revoked {} session(s) for a deactivated account: userId={}", revoked, event.userId());
    }
}
