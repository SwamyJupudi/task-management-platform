package com.company.taskmanagementplatform.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** The live sessions of one person, most recent first. */
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(UUID userId);

    Optional<RefreshToken> findFirstByUserIdAndSessionIdAndRevokedAtIsNull(UUID userId, UUID sessionId);

    /**
     * Revokes every live token of one person in a single statement.
     *
     * <p>A bulk update rather than a load-and-mutate loop. Somebody with many sessions is exactly the
     * case where this is called, and it is called at the moments that matter most: a password change,
     * a reset, a deactivation.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now, t.revokedReason = :reason
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("reason") RevocationReason reason,
            @Param("now") Instant now);

    /**
     * Claims a token for rotation, atomically.
     *
     * <p>This is the whole concurrency guard. Reading the row, deciding it is live, and then writing
     * leaves a window in which two requests both decide the same thing and both mint a successor,
     * quietly turning one session into two. Doing it as a conditional update moves the decision into
     * the database: the {@code revoked_at IS NULL} predicate is evaluated under the row lock, so
     * exactly one caller can ever see a row count of one.
     *
     * @return 1 if this caller claimed the token, 0 if it was already consumed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now, t.revokedReason = com.company.taskmanagementplatform.auth.RevocationReason.ROTATED
            WHERE t.id = :id AND t.revokedAt IS NULL
            """)
    int claimForRotation(@Param("id") UUID id, @Param("now") Instant now);

    /** Links a claimed token to the one that replaced it, completing the chain. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.replacedById = :successorId WHERE t.id = :id")
    int linkSuccessor(@Param("id") UUID id, @Param("successorId") UUID successorId);

    /** Revokes an entire rotation chain, used when a consumed token is presented again. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now, t.revokedReason = :reason
            WHERE t.sessionId = :sessionId AND t.revokedAt IS NULL
            """)
    int revokeSession(
            @Param("sessionId") UUID sessionId,
            @Param("reason") RevocationReason reason,
            @Param("now") Instant now);
}
