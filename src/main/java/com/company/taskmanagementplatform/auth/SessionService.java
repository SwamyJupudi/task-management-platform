package com.company.taskmanagementplatform.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.auth.dto.SessionResponse;

/**
 * Lets somebody see and end their own sessions, which is the visible half of token management.
 *
 * <p>Only ever their own. There is no endpoint for looking at anybody else's, because a list of where
 * and when a person signs in is not something an administrator needs and is very much something an
 * attacker would like.
 */
@Service
public class SessionService {

    private final RefreshTokenService refreshTokens;

    SessionService(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /**
     * @param currentRawToken the refresh token on this request, so the caller's own session can be
     *     marked in the list; null when no cookie was presented
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> list(UUID userId, String currentRawToken) {
        UUID currentSessionId =
                currentRawToken == null ? null : refreshTokens.sessionIdOf(currentRawToken).orElse(null);

        return refreshTokens.activeSessions(userId).stream()
                .map(token -> new SessionResponse(
                        token.getSessionId(),
                        token.getUserAgent(),
                        token.getIpAddress(),
                        token.getIssuedAt(),
                        token.getExpiresAt(),
                        token.getSessionId().equals(currentSessionId)))
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        refreshTokens.revokeSession(userId, sessionId);
    }
}
