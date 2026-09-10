package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.auth.RefreshTokenService;
import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;

/**
 * Two requests holding the same live refresh token must not both succeed.
 *
 * <p>Reading the row, deciding it is live, then writing leaves a window in which both callers decide
 * the same thing and both mint a successor, quietly turning one session into two. The conditional
 * update closes it by moving the decision into the database, where the predicate is evaluated under
 * the row lock.
 *
 * <p>The losing caller is treated as reuse rather than as a harmless duplicate. From inside the
 * request there is no way to tell a double submit from a stolen token being replayed, and only one
 * of those two readings is safe.
 */
class RefreshTokenConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onlyOneOfTwoConcurrentRotationsSucceeds() throws Exception {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("concurrent"));
        RefreshTokenService.IssuedRefreshToken issued = refreshTokens.issue(person.id(), "Test", "127.0.0.1");

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        // A barrier rather than a sleep, so both threads are genuinely inside the
        // window at the same moment instead of merely near it.
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Void> attempt = () -> {
            startTogether.await(10, TimeUnit.SECONDS);
            try {
                refreshTokens.rotate(issued.rawToken(), "Test", "127.0.0.1");
                succeeded.incrementAndGet();
            } catch (RuntimeException e) {
                refused.incrementAndGet();
            }
            return null;
        };

        try {
            for (Future<Void> outcome : pool.invokeAll(List.of(attempt, attempt))) {
                outcome.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded.get()).as("rotations that succeeded").isEqualTo(1);
        assertThat(refused.get()).as("rotations that were refused").isEqualTo(1);
    }

    @Test
    void theLosingRotationRevokesTheSessionAsReuse() throws Exception {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("family"));
        RefreshTokenService.IssuedRefreshToken issued = refreshTokens.issue(person.id(), "Test", "127.0.0.1");

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Void> attempt = () -> {
            startTogether.await(10, TimeUnit.SECONDS);
            try {
                refreshTokens.rotate(issued.rawToken(), "Test", "127.0.0.1");
            } catch (RuntimeException ignored) {
                // The refusal is the subject of the previous test.
            }
            return null;
        };

        try {
            for (Future<Void> outcome : pool.invokeAll(List.of(attempt, attempt))) {
                outcome.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Integer reuseRevocations = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE session_id = ? AND revoked_reason = 'REUSE_DETECTED'",
                Integer.class,
                issued.sessionId());

        assertThat(reuseRevocations).as("tokens revoked as reuse in this session").isPositive();
        assertThat(refreshTokens.activeSessions(person.id())).isEmpty();
    }

    @Test
    void replayingAConsumedTokenEndsEveryTokenInTheFamily() {
        // The four steps of the reuse rule, asserted at the row level so that a
        // revocation which is written and then rolled back cannot pass as a
        // revocation. That is exactly how this failed before: the caller was refused,
        // the session survived, and only the row count showed it.
        UserAccount person = fixtures.verifiedUser(uniqueEmail("replay-family"));
        RefreshTokenService.IssuedRefreshToken first = refreshTokens.issue(person.id(), "Test", "127.0.0.1");

        // a) the first use of a token succeeds
        RefreshTokenService.RotatedRefreshToken second =
                refreshTokens.rotate(first.rawToken(), "Test", "127.0.0.1");
        assertThat(refreshTokens.activeSessions(person.id())).hasSize(1);

        // b) presenting the consumed token again is refused
        assertThatThrownBy(() -> refreshTokens.rotate(first.rawToken(), "Test", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(thrown ->
                        assertThat(((UnauthorizedException) thrown).errorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));

        // c) the refusal ends the whole family, and the eviction survives it
        Integer reuseRevocations = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE session_id = ? AND revoked_reason = 'REUSE_DETECTED'",
                Integer.class,
                first.sessionId());
        assertThat(reuseRevocations).as("tokens revoked as reuse in this session").isPositive();
        assertThat(refreshTokens.activeSessions(person.id())).isEmpty();

        // d) the successor that was legitimately issued cannot be used either
        assertThatThrownBy(() -> refreshTokens.rotate(second.token().rawToken(), "Test", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void sequentialRotationLeavesExactlyOneLiveToken() {
        // The ordinary path, asserted at the row level rather than through the API,
        // so a chain that quietly forked would be visible here.
        UserAccount person = fixtures.verifiedUser(uniqueEmail("sequential"));
        RefreshTokenService.IssuedRefreshToken first = refreshTokens.issue(person.id(), "Test", "127.0.0.1");

        RefreshTokenService.RotatedRefreshToken second =
                refreshTokens.rotate(first.rawToken(), "Test", "127.0.0.1");
        refreshTokens.rotate(second.token().rawToken(), "Test", "127.0.0.1");

        Integer live = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE session_id = ? AND revoked_at IS NULL",
                Integer.class,
                first.sessionId());

        assertThat(live).isEqualTo(1);
    }

    @Test
    void rotationLinksEachTokenToItsSuccessor() {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("chain"));
        RefreshTokenService.IssuedRefreshToken first = refreshTokens.issue(person.id(), "Test", "127.0.0.1");

        RefreshTokenService.RotatedRefreshToken second =
                refreshTokens.rotate(first.rawToken(), "Test", "127.0.0.1");

        java.util.UUID replacedBy = jdbc.queryForObject(
                "SELECT replaced_by_id FROM refresh_tokens WHERE id = ?", java.util.UUID.class, first.id());

        assertThat(replacedBy).isEqualTo(second.token().id());
    }
}
