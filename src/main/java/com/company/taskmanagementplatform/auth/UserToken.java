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
 * A single-use token sent to an address: email verification, or password reset.
 *
 * <p>Single use is enforced by a consumption timestamp rather than by deleting the row, so a token
 * presented twice can be told apart from one that never existed. Both are refused, but only one of
 * them is worth a log line.
 */
@Entity
@Table(name = "user_tokens")
class UserToken {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private UserTokenType type;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserToken() {
        // for JPA
    }

    static UserToken issue(UUID userId, UserTokenType type, String tokenHash, Instant expiresAt) {
        UserToken token = new UserToken();
        token.userId = userId;
        token.type = type;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
    }

    boolean isConsumed() {
        return consumedAt != null;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    void consume(Instant now) {
        consumedAt = now;
    }

    UUID getUserId() {
        return userId;
    }

    UserTokenType getType() {
        return type;
    }
}
