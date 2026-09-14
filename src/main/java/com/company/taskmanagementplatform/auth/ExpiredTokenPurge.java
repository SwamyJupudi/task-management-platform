package com.company.taskmanagementplatform.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.function.IntUnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.scheduling.AdvisoryLock;
import com.company.taskmanagementplatform.common.scheduling.LockKeys;

/**
 * Removes verification, reset, invitation and refresh tokens that expired long enough ago to be of no
 * use to anybody.
 *
 * <p>Nothing deleted these before. Both tables grow with every sign-in, every rotation, every reset
 * link and every invitation, and nothing shrank them, so the row that authenticated somebody in the
 * first week of the installation was still there years later. Two tables, one job, because they have
 * the same shape of row and the same reason for going.
 *
 * <p><strong>Expiry plus a retention window, not expiry alone.</strong> An expired token is already
 * refused by every lookup, so the retention is not protecting anything the application needs; it is
 * there so that a question asked a few days late — why a reset link did not work, whether an invitation
 * was ever issued — can still be answered from the data rather than from whatever the logs kept. Thirty
 * days is long enough for that and short enough that the tables stay proportional to recent activity.
 * {@code RefreshTokenRepository} explains why the predicate must be expiry rather than revocation.
 *
 * <p><strong>Batched, and each batch is its own transaction.</strong> The first run after this is
 * switched on has the whole history to remove, and a single unbounded {@code DELETE} would hold locks
 * on the busiest tables in the schema for as long as that took, while every sign-in waited. The
 * transaction boundary is on the repository method, so one batch commits on its own: a batch that fails
 * leaves the ones before it committed and the next run continues from there.
 *
 * <p><strong>Nothing here runs inside a request.</strong> The only entry points are the scheduler and
 * the tests. The job itself opens no transaction, so it cannot hold one open across a whole run, and
 * there is no path by which a request could reach it and enlist it in its own.
 *
 * <p>One instance at a time, by the same advisory lock the deadline scan uses. Overlapping runs would be
 * harmless — the second would simply find fewer rows — but they would be two connections doing one
 * job's work.
 */
@Component
class ExpiredTokenPurge {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenPurge.class);

    /**
     * The most batches one run will do, per table.
     *
     * <p>A stop, not a target. Without it a run whose statement kept reporting a full batch would loop
     * for as long as rows kept arriving, which is a job that never ends rather than a job that finishes
     * early. At the default batch size this is a quarter of a million rows in one night; whatever is
     * left waits for tomorrow, which is exactly what a nightly job is for.
     */
    private static final int MAX_BATCHES = 500;

    private final UserTokenRepository userTokens;
    private final RefreshTokenRepository refreshTokens;
    private final AdvisoryLock lock;
    private final TokenPurgeProperties properties;
    private final Clock clock;

    ExpiredTokenPurge(
            UserTokenRepository userTokens,
            RefreshTokenRepository refreshTokens,
            AdvisoryLock lock,
            TokenPurgeProperties properties,
            Clock clock) {
        this.userTokens = userTokens;
        this.refreshTokens = refreshTokens;
        this.lock = lock;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The scheduled entry point, which does nothing unless the purge was switched on.
     *
     * <p>The check is here rather than on the bean so the tests can drive the purge directly. A test
     * that waited for half past three in the morning would not be a test.
     */
    @Scheduled(cron = "${app.auth.token-purge.cron}")
    void scheduled() {
        if (!properties.enabled()) {
            return;
        }

        try {
            run();
        } catch (RuntimeException e) {
            // A scheduled method that throws is logged by the framework and not
            // retried. This says which job it was.
            log.error("The expired-token purge failed", e);
        }
    }

    /**
     * Purges once, if this instance gets the lock.
     *
     * @return what was deleted, and zeroes when another instance holds the lock
     */
    Result run() {
        Instant cutoff = clock.instant().minus(properties.retention());
        int[] deleted = new int[2];

        boolean ran = lock.runExclusively(LockKeys.EXPIRED_TOKEN_PURGE, () -> {
            deleted[0] = purge("user_tokens", batch -> userTokens.deleteExpiredBatch(cutoff, batch));
            deleted[1] = purge("refresh_tokens", batch -> refreshTokens.deleteExpiredBatch(cutoff, batch));
        });

        if (!ran) {
            return new Result(0, 0);
        }

        Result result = new Result(deleted[0], deleted[1]);
        log.info(
                "Expired-token purge complete: cutoff={} singleUseTokens={} refreshTokens={}",
                cutoff,
                result.userTokens(),
                result.refreshTokens());
        return result;
    }

    /**
     * Deletes batch after batch until one comes back short, which means the backlog is gone.
     *
     * <p>A short batch is the only reliable signal that there is nothing left. Counting rows first and
     * dividing would race with every sign-in happening at the same time.
     */
    private int purge(String table, IntUnaryOperator deleteBatch) {
        int batchSize = properties.batchSize();
        int total = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int removed = deleteBatch.applyAsInt(batchSize);
            total += removed;

            if (removed < batchSize) {
                return total;
            }
        }

        log.warn(
                "The purge of {} stopped at its batch limit with rows still eligible; "
                        + "the next run will continue. deleted={}",
                table,
                total);
        return total;
    }

    /** What one run removed, by table. */
    record Result(int userTokens, int refreshTokens) {

        int total() {
            return userTokens + refreshTokens;
        }
    }
}
