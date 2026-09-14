package com.company.taskmanagementplatform.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    /**
     * Consumes every outstanding token of one kind for one person.
     *
     * <p>Called when a reset link is used and when a new one is requested, so that only the newest
     * link ever works. Leaving older ones live would widen the window an intercepted message gives an
     * attacker.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserToken t
            SET t.consumedAt = :now
            WHERE t.userId = :userId AND t.type = :type AND t.consumedAt IS NULL
            """)
    int consumeAllOfType(
            @Param("userId") UUID userId, @Param("type") UserTokenType type, @Param("now") Instant now);

    /**
     * Deletes a bounded batch of tokens that expired before a cutoff, oldest first.
     *
     * <p>Native rather than JPQL, and for one reason: JPQL cannot put a limit on a {@code DELETE}. An
     * unbounded delete over this table would be a single statement holding locks for as long as the
     * backlog took, and the first run after the purge is switched on is the largest backlog there will
     * ever be. The subquery names the rows, the outer statement removes them, and the caller loops
     * until a run deletes fewer than it asked for.
     *
     * <p>The predicate is expiry, not consumption. A consumed token is already refused by every lookup,
     * and an unconsumed one that has expired is refused too, so expiry is the point past which the row
     * cannot affect any decision. Deleting on consumption instead would remove the newest rows first,
     * which is the opposite of what a retention window means.
     *
     * @param expiredBefore rows whose {@code expires_at} is strictly before this are eligible
     * @param batchSize the most rows to delete in this statement
     * @return how many were deleted
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    // Its own write transaction, because the purge that calls this runs on the
    // scheduler with none. Spring Data's repositories are read-only by default,
    // which a DELETE cannot run under, and one transaction per batch is exactly
    // the boundary wanted: a batch that fails leaves the batches before it
    // committed rather than undoing a whole night's work.
    @org.springframework.transaction.annotation.Transactional
    @Query(
            nativeQuery = true,
            value = """
            DELETE FROM user_tokens
            WHERE id IN (
                SELECT id FROM user_tokens
                WHERE expires_at < :expiredBefore
                ORDER BY expires_at
                LIMIT :batchSize
            )
            """)
    int deleteExpiredBatch(@Param("expiredBefore") Instant expiredBefore, @Param("batchSize") int batchSize);
}
