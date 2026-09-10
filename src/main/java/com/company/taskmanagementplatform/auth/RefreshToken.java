package com.company.taskmanagementplatform.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One issued refresh token.
 *
 * <p>A row per token rather than per session, because rotation needs the history. Each use writes a
 * new row and marks the old one replaced, so a chain forms. If a token that has already been
 * replaced is presented again, the chain identifies every token in that session and all of them are
 * revoked at once.
 *
 * <p>Only the hash is stored, so this table is of no use to anybody who copies it.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Constant across a rotation chain. This is what "a session" means here. */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason")
    private RevocationReason revokedReason;

    /**
     * Set by {@code RefreshTokenRepository.linkSuccessor} once the replacement exists.
     *
     * <p>Written by a bulk update rather than by dirty checking, because rotation claims the row with
     * a conditional update and that clears the persistence context, detaching this entity.
     */
    @Column(name = "replaced_by_id")
    private UUID replacedById;

    /** Kept so a person can recognise their own sessions. Never logged, never shown to anyone else. */
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // for JPA
    }

    static RefreshToken issue(
            UUID userId,
            UUID sessionId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            String userAgent,
            String ipAddress) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.sessionId = sessionId;
        token.tokenHash = tokenHash;
        token.issuedAt = issuedAt;
        token.expiresAt = expiresAt;
        token.userAgent = userAgent;
        token.ipAddress = ipAddress;
        return token;
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
        if (issuedAt == null) {
            issuedAt = createdAt;
        }
    }

    void revoke(RevocationReason reason, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason;
        }
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getSessionId() {
        return sessionId;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    String getUserAgent() {
        return userAgent;
    }

    String getIpAddress() {
        return ipAddress;
    }
}
