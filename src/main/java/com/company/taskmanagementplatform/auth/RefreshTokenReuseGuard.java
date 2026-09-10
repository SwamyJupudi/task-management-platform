package com.company.taskmanagementplatform.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a rotation chain when a consumed token is presented again.
 *
 * <p>This exists for one reason: the revocation has to outlive the refusal that follows it. Reuse is
 * discovered part way through a refresh, and the refusal is an exception, so the transaction the
 * refresh runs in is rolled back. A revocation written in that transaction would be rolled back with
 * it, leaving the caller refused but the session very much alive, which is the opposite of what reuse
 * detection is for. Running in a transaction of its own means the eviction is committed before the
 * refusal unwinds anything.
 *
 * <p>It is a separate bean because a call from one method to another on the same object does not pass
 * through the proxy, and the propagation setting below would be quietly ignored.
 */
@Service
class RefreshTokenReuseGuard {

    private final RefreshTokenRepository tokens;

    RefreshTokenReuseGuard(RefreshTokenRepository tokens) {
        this.tokens = tokens;
    }

    /**
     * Ends every live token in the session.
     *
     * @return how many tokens were revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int revokeSessionAsReuse(UUID sessionId, Instant now) {
        return tokens.revokeSession(sessionId, RevocationReason.REUSE_DETECTED, now);
    }
}
