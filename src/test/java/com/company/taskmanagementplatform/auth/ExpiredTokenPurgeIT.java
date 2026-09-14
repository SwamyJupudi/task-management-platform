package com.company.taskmanagementplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;

/**
 * What the token purge removes, and the three things it must leave alone.
 *
 * <p>Rows are inserted through JDBC with the expiry the test wants, rather than by waiting. The
 * retention is the real thirty days in the test profile on purpose: a window shrunk until everything
 * qualifies would prove the delete works and nothing about the window.
 */
class ExpiredTokenPurgeIT extends AbstractIntegrationTest {

    @Autowired
    private ExpiredTokenPurge purge;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private TokenPurgeProperties properties;

    @Test
    void removesASingleUseTokenThatExpiredBeyondTheRetention() {
        UUID user = person();
        UUID old = insertUserToken(user, daysAgo(40));

        purge.run();

        assertThat(userTokenExists(old)).isFalse();
    }

    @Test
    void keepsASingleUseTokenThatExpiredInsideTheRetention() {
        // Expired, so already useless to anybody; kept anyway, so that "why did my
        // reset link not work last Tuesday" is answerable from the data.
        UUID user = person();
        UUID recent = insertUserToken(user, daysAgo(3));

        purge.run();

        assertThat(userTokenExists(recent)).isTrue();
    }

    @Test
    void keepsASingleUseTokenThatHasNotExpired() {
        UUID user = person();
        UUID live = insertUserToken(user, Instant.now().plus(Duration.ofDays(1)));

        purge.run();

        assertThat(userTokenExists(live)).isTrue();
    }

    @Test
    void removesARefreshTokenThatExpiredBeyondTheRetention() {
        UUID user = person();
        UUID old = insertRefreshToken(user, daysAgo(40), null);

        purge.run();

        assertThat(refreshTokenExists(old)).isFalse();
    }

    @Test
    void keepsARevokedRefreshTokenThatHasNotExpiredBecauseReuseDetectionNeedsIt() {
        // This is the assertion that matters most here. Presenting a revoked token is
        // how a stolen rotation chain is discovered; a row deleted while it could
        // still be presented would turn a detected replay into a lookup that finds
        // nothing, and the session would survive.
        UUID user = person();
        UUID revokedButLive =
                insertRefreshToken(user, Instant.now().plus(Duration.ofDays(7)), Instant.now().minusSeconds(60));

        purge.run();

        assertThat(refreshTokenExists(revokedButLive)).isTrue();
    }

    @Test
    void removesARefreshTokenThatWasRevokedAndHasSinceExpired() {
        // Once it has expired the expiry check refuses it before reuse is ever
        // considered, so there is no signal left to lose.
        UUID user = person();
        UUID gone = insertRefreshToken(user, daysAgo(40), daysAgo(45));

        purge.run();

        assertThat(refreshTokenExists(gone)).isFalse();
    }

    @Test
    void clearsABacklogLargerThanOneBatch() {
        // The batch size is two in the test profile, so five rows means three passes.
        // Without the loop the first run would leave three behind.
        UUID user = person();
        UUID[] old = new UUID[5];
        for (int i = 0; i < old.length; i++) {
            old[i] = insertUserToken(user, daysAgo(40 + i));
        }

        ExpiredTokenPurge.Result result = purge.run();

        assertThat(properties.batchSize()).isEqualTo(2);
        assertThat(result.userTokens()).isGreaterThanOrEqualTo(5);
        for (UUID id : old) {
            assertThat(userTokenExists(id)).isFalse();
        }
    }

    @Test
    void reportsWhatItRemovedFromEachTable() {
        UUID user = person();
        insertUserToken(user, daysAgo(40));
        insertRefreshToken(user, daysAgo(40), null);

        ExpiredTokenPurge.Result result = purge.run();

        assertThat(result.userTokens()).isGreaterThanOrEqualTo(1);
        assertThat(result.refreshTokens()).isGreaterThanOrEqualTo(1);
        assertThat(result.total()).isEqualTo(result.userTokens() + result.refreshTokens());
    }

    @Test
    void theScheduledEntryPointDoesNothingWhileTheJobIsSwitchedOff() {
        // Off in every profile by default, because this job deletes data. The test
        // profile leaves it off, so the scheduled method must be inert.
        UUID user = person();
        UUID old = insertUserToken(user, daysAgo(40));

        assertThat(properties.enabled()).isFalse();
        purge.scheduled();

        assertThat(userTokenExists(old)).isTrue();
    }

    private UUID person() {
        UserAccount account = fixtures.verifiedUser(uniqueEmail("purge"));
        return account.id();
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    private UUID insertUserToken(UUID userId, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO user_tokens (id, user_id, type, token_hash, expires_at, created_at)
                VALUES (?, ?, 'PASSWORD_RESET', ?, ?, now())
                """,
                id,
                userId,
                "hash-" + id,
                java.sql.Timestamp.from(expiresAt));
        return id;
    }

    private UUID insertRefreshToken(UUID userId, Instant expiresAt, Instant revokedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO refresh_tokens
                    (id, user_id, session_id, token_hash, issued_at, expires_at, revoked_at, revoked_reason)
                VALUES (?, ?, ?, ?, now(), ?, ?, ?)
                """,
                id,
                userId,
                UUID.randomUUID(),
                "hash-" + id,
                java.sql.Timestamp.from(expiresAt),
                revokedAt == null ? null : java.sql.Timestamp.from(revokedAt),
                revokedAt == null ? null : "LOGOUT");
        return id;
    }

    private boolean userTokenExists(UUID id) {
        return count("SELECT count(*) FROM user_tokens WHERE id = ?", id) == 1;
    }

    private boolean refreshTokenExists(UUID id) {
        return count("SELECT count(*) FROM refresh_tokens WHERE id = ?", id) == 1;
    }

    private int count(String sql, UUID id) {
        Integer found = jdbc.queryForObject(sql, Integer.class, id);
        return found == null ? 0 : found;
    }
}
