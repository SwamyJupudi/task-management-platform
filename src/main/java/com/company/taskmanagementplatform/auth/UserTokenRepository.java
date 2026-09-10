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
}
