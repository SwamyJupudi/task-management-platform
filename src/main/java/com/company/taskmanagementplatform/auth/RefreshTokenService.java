package com.company.taskmanagementplatform.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.common.security.TokenHasher;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>The rotation rule is the security-relevant part. Every use produces a new token and retires the
 * one presented, so a token is valid exactly once. If a retired token turns up again, that is not a
 * client bug worth tolerating: it means the value existed in two places, and since only one of them
 * can be the legitimate holder, the whole session is revoked and both parties have to sign in again.
 * The legitimate user is inconvenienced; the attacker is evicted. That is the right way round.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository tokens;
    private final RefreshTokenReuseGuard reuseGuard;
    private final TokenHasher hasher;
    private final SecurityProperties.Cookie cookieProperties;
    private final Clock clock;

    RefreshTokenService(
            RefreshTokenRepository tokens,
            RefreshTokenReuseGuard reuseGuard,
            TokenHasher hasher,
            SecurityProperties securityProperties,
            Clock clock) {
        this.tokens = tokens;
        this.reuseGuard = reuseGuard;
        this.hasher = hasher;
        this.cookieProperties = securityProperties.cookie();
        this.clock = clock;
    }

    /** Starts a new session. */
    @Transactional
    public IssuedRefreshToken issue(UUID userId, String userAgent, String ipAddress) {
        return issueInSession(userId, UUID.randomUUID(), userAgent, ipAddress);
    }

    /**
     * Exchanges a token for its successor.
     *
     * @throws UnauthorizedException if the token is unknown, already used, or expired
     */
    @Transactional
    public RotatedRefreshToken rotate(String rawToken, String userAgent, String ipAddress) {
        RefreshToken presented =
                tokens.findByTokenHash(hasher.hash(rawToken)).orElseThrow(UnauthorizedException::tokenInvalid);

        Instant now = clock.instant();

        // Read before claiming: the claim is a bulk update and clears the
        // persistence context, which would detach the entity underneath us.
        UUID presentedId = presented.getId();
        UUID userId = presented.getUserId();
        UUID sessionId = presented.getSessionId();
        boolean expired = presented.isExpired(now);

        if (expired) {
            // Ordinary rather than suspicious. Ending every session over an expiry
            // would sign people out for nothing.
            throw UnauthorizedException.tokenExpired();
        }

        // The claim decides who wins. Two requests holding the same live token both
        // reach this line; the conditional update is evaluated under the row lock,
        // so exactly one of them is told it claimed the row.
        if (tokens.claimForRotation(presentedId, now) == 0) {
            // Either a replay of a token consumed long ago, or the loser of a race
            // that happened just now. The two are indistinguishable from here, and
            // the safe reading is the first: the value was in two places at once.
            log.warn(
                    "Refresh token reuse detected; revoking the session: userId={} sessionId={}",
                    userId,
                    sessionId);
            // Committed in its own transaction. The refusal below is an exception, so
            // this one is rolled back with it otherwise, and the session it was meant
            // to end would survive the attempt to end it.
            reuseGuard.revokeSessionAsReuse(sessionId, now);
            throw UnauthorizedException.tokenInvalid();
        }

        IssuedRefreshToken successor = issueInSession(userId, sessionId, userAgent, ipAddress);
        tokens.linkSuccessor(presentedId, successor.id());

        return new RotatedRefreshToken(userId, successor);
    }

    @Transactional
    public void revokeByRawToken(String rawToken, RevocationReason reason) {
        tokens.findByTokenHash(hasher.hash(rawToken)).ifPresent(token -> token.revoke(reason, clock.instant()));
    }

    @Transactional
    public int revokeAllForUser(UUID userId, RevocationReason reason) {
        return tokens.revokeAllForUser(userId, reason, clock.instant());
    }

    /** Ends one named session, used by the endpoint that lists them. */
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        tokens.findFirstByUserIdAndSessionIdAndRevokedAtIsNull(userId, sessionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Session", sessionId));
        tokens.revokeSession(sessionId, RevocationReason.SESSION_REVOKED, clock.instant());
    }

    /**
     * The live sessions of one person.
     *
     * <p>One live token per session, because rotation retires the previous one, so the live tokens
     * are the sessions.
     */
    @Transactional(readOnly = true)
    public List<RefreshToken> activeSessions(UUID userId) {
        Instant now = clock.instant();
        return tokens.findAllByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(userId).stream()
                .filter(token -> !token.isExpired(now))
                .toList();
    }

    /** The session a raw token belongs to, so the caller's own row can be marked in a list. */
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> sessionIdOf(String rawToken) {
        return tokens.findByTokenHash(hasher.hash(rawToken)).map(RefreshToken::getSessionId);
    }

    private IssuedRefreshToken issueInSession(UUID userId, UUID sessionId, String userAgent, String ipAddress) {
        String rawToken = hasher.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(cookieProperties.ttl());

        RefreshToken saved = tokens.save(RefreshToken.issue(
                userId, sessionId, hasher.hash(rawToken), now, expiresAt, truncate(userAgent, 400), ipAddress));

        return new IssuedRefreshToken(saved.getId(), sessionId, rawToken, expiresAt);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * @param rawToken returned once, to be put in the cookie, and never stored
     */
    public record IssuedRefreshToken(UUID id, UUID sessionId, String rawToken, Instant expiresAt) {}

    public record RotatedRefreshToken(UUID userId, IssuedRefreshToken token) {}
}
