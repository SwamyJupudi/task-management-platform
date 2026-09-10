package com.company.taskmanagementplatform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.security.TokenHasher;

/**
 * Issues and redeems the tokens that are sent to an address and work exactly once.
 *
 * <p>Shared by verification and password reset, because the two have identical mechanics and only
 * differ in lifetime and meaning. Writing it twice would mean maintaining the single-use rule twice.
 */
@Service
class SingleUseTokenService {

    private final UserTokenRepository tokens;
    private final TokenHasher hasher;
    private final Clock clock;

    SingleUseTokenService(UserTokenRepository tokens, TokenHasher hasher, Clock clock) {
        this.tokens = tokens;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * Issues a token, retiring any earlier one of the same kind for the same person.
     *
     * <p>Retiring the old ones matters. If three reset messages are outstanding, three intercepted
     * messages are three ways in; only the newest link should work.
     *
     * @return the raw token, which exists here and in the outgoing message and nowhere else
     */
    @Transactional
    public String issue(UUID userId, UserTokenType type, Duration ttl) {
        Instant now = clock.instant();
        tokens.consumeAllOfType(userId, type, now);

        String rawToken = hasher.generate();
        tokens.save(UserToken.issue(userId, type, hasher.hash(rawToken), now.plus(ttl)));
        return rawToken;
    }

    /**
     * Redeems a token, or explains why it cannot be.
     *
     * @return the account the token belongs to
     * @throws UnauthorizedException if it is unknown, of the wrong kind, already used, or expired
     */
    @Transactional
    public UUID consume(String rawToken, UserTokenType expectedType) {
        UserToken token =
                tokens.findByTokenHash(hasher.hash(rawToken)).orElseThrow(UnauthorizedException::tokenInvalid);

        // A token of the wrong kind is refused rather than accepted, so a
        // verification link can never be used to set a password.
        if (token.getType() != expectedType || token.isConsumed()) {
            throw UnauthorizedException.tokenInvalid();
        }

        Instant now = clock.instant();
        if (token.isExpired(now)) {
            throw UnauthorizedException.tokenExpired();
        }

        token.consume(now);
        return token.getUserId();
    }
}
