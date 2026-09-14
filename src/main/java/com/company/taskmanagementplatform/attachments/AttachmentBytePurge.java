package com.company.taskmanagementplatform.attachments;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.scheduling.AdvisoryLock;
import com.company.taskmanagementplatform.common.scheduling.LockKeys;

/**
 * Reclaims the stored objects behind attachments that were deleted long enough ago that nobody is going
 * to ask for them back.
 *
 * <p>This is the job the attachments module has been pointing at since it was written. Deleting a file
 * has always been soft, so the row was marked and the object left where it was, and every class in the
 * module that could see the consequence said so: {@code AttachmentCleanupListener}, {@code
 * AttachmentService.delete}, {@code AttachmentAdminFacade} and {@code FileStore.delete} each recorded
 * that the store grows until this exists. It exists now.
 *
 * <p><strong>The object goes first, then the row, and never the other way round.</strong> That order is
 * the one invariant here and it is not a preference. A row deleted before its object leaves bytes in the
 * store that nothing in the schema can name any more: not findable, not listable, not attributable to a
 * workspace, and still being paid for. An object deleted before its row leaves a row pointing at
 * nothing, which the next run simply deletes — the store reports an already-absent object as a success,
 * so the second attempt succeeds and the row goes. One order is self-healing; the other loses data
 * permanently. {@code FileStore.delete} carries the matching half of this contract: it must throw while
 * the object might still exist.
 *
 * <p><strong>One attachment per transaction, and the transaction opens after the object is gone.</strong>
 * This job opens none of its own: each row is removed by {@code deleteById}, which Spring Data runs in a
 * transaction of its own when there is no ambient one, and there is never an ambient one here. The
 * alternative — one transaction around the batch — would mean a store call failing on the last of two
 * hundred rolled back the row deletions for the other hundred and ninety-nine, whose objects had already
 * been removed, and it would hold a database transaction open across two hundred network calls to a
 * storage provider. A transaction per row costs more round trips and is the one that cannot corrupt.
 *
 * <p><strong>A file that will not delete does not stop the run.</strong> The failure is logged with the
 * attachment's identifier and the run moves to the next: one unreachable object should not indefinitely
 * postpone reclaiming every other. The row stays, so the next run tries again, and a permanently
 * unreachable object shows up as a warning every night rather than as a job that never completes.
 *
 * <p><strong>Nothing here runs inside a request.</strong> The scheduler and the tests are the only entry
 * points, and the job holds no transaction of its own, so there is no path by which a request could reach
 * it and enlist it in one.
 *
 * <p>One instance at a time, by the same advisory lock the other two jobs use. Overlapping runs here
 * would be worse than merely wasteful: two instances could pick the same attachment and the second would
 * try to delete a row the first had already removed.
 */
@Component
class AttachmentBytePurge {

    private static final Logger log = LoggerFactory.getLogger(AttachmentBytePurge.class);

    /**
     * The most batches one run will do.
     *
     * <p>A stop rather than a target, for the reason {@code ExpiredTokenPurge} gives. Whatever is left
     * waits for tomorrow.
     */
    private static final int MAX_BATCHES = 100;

    private final AttachmentRepository attachments;
    private final FileStore store;
    private final AdvisoryLock lock;
    private final AttachmentPurgeProperties properties;
    private final Clock clock;

    AttachmentBytePurge(
            AttachmentRepository attachments,
            FileStore store,
            AdvisoryLock lock,
            AttachmentPurgeProperties properties,
            Clock clock) {
        this.attachments = attachments;
        this.store = store;
        this.lock = lock;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The scheduled entry point, which does nothing unless the purge was switched on.
     *
     * <p>The check is here rather than on the bean so the tests can drive the purge directly.
     */
    @Scheduled(cron = "${app.storage.purge.cron}")
    void scheduled() {
        if (!properties.enabled()) {
            return;
        }

        try {
            run();
        } catch (RuntimeException e) {
            log.error("The attachment byte purge failed", e);
        }
    }

    /**
     * Purges once, if this instance gets the lock.
     *
     * @return what was reclaimed and what refused to go, and zeroes when another instance holds the lock
     */
    Result run() {
        Instant cutoff = clock.instant().minus(properties.retention());
        int[] counts = new int[2];

        boolean ran = lock.runExclusively(LockKeys.ATTACHMENT_BYTE_PURGE, () -> {
            Result result = purge(cutoff);
            counts[0] = result.reclaimed();
            counts[1] = result.failed();
        });

        if (!ran) {
            return new Result(0, 0);
        }

        Result result = new Result(counts[0], counts[1]);
        if (result.failed() > 0) {
            log.warn(
                    "Attachment byte purge complete with failures: cutoff={} reclaimed={} failed={}",
                    cutoff,
                    result.reclaimed(),
                    result.failed());
        } else {
            log.info("Attachment byte purge complete: cutoff={} reclaimed={}", cutoff, result.reclaimed());
        }
        return result;
    }

    /**
     * Walks the backlog, oldest deletion first, until there is nothing left it can reclaim.
     *
     * <p>Each pass re-reads the <em>first</em> page rather than paging forward by number, because every
     * success removes a row and would shift every later page under its own feet. The rows that failed
     * stay, so they are read again; they are held in {@code unreclaimable}, skipped, and the page is
     * widened by their number so that a page full of rows this run cannot touch still reaches the ones
     * behind them.
     *
     * <p>Two conditions end a run early, and they mean different things. Finding nothing new to try means
     * the backlog is done. Accumulating a whole batch of failures means the problem is very unlikely to be
     * the individual objects and very likely to be the store itself, and hammering an unreachable provider
     * once per remaining row is worse than stopping and saying so — the rows all survive either way, so
     * nothing is lost by waiting for the next run.
     */
    private Result purge(Instant cutoff) {
        int batchSize = properties.batchSize();
        java.util.Set<java.util.UUID> unreclaimable = new java.util.HashSet<>();
        int reclaimed = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Attachment> due =
                    attachments.findPurgeable(cutoff, PageRequest.of(0, batchSize + unreclaimable.size()));

            List<Attachment> pending = due.stream()
                    .filter(attachment -> !unreclaimable.contains(attachment.getId()))
                    .toList();
            if (pending.isEmpty()) {
                return new Result(reclaimed, unreclaimable.size());
            }

            for (Attachment attachment : pending) {
                if (reclaim(attachment)) {
                    reclaimed++;
                } else {
                    unreclaimable.add(attachment.getId());
                }
            }

            if (unreclaimable.size() >= batchSize) {
                log.warn(
                        "The attachment byte purge stopped after {} consecutive failures, which points at the "
                                + "object store rather than at the files; the next run will try again. reclaimed={}",
                        unreclaimable.size(),
                        reclaimed);
                return new Result(reclaimed, unreclaimable.size());
            }
        }

        log.warn(
                "The attachment byte purge stopped at its batch limit with files still eligible; "
                        + "the next run will continue. reclaimed={} failed={}",
                reclaimed,
                unreclaimable.size());
        return new Result(reclaimed, unreclaimable.size());
    }

    /**
     * Removes one attachment's bytes and then its row.
     *
     * @return whether the bytes are gone and the row with them
     */
    private boolean reclaim(Attachment attachment) {
        try {
            store.delete(attachment.getStorageKey());
        } catch (RuntimeException e) {
            // The object may still be there, so the row must stay: it is the only
            // thing that still knows the key. Logged with the identifier and never
            // with the key, which is internal, and never with any of the content.
            log.warn(
                    "Could not reclaim the stored object for attachment {}; keeping the row so the next "
                            + "run can try again",
                    attachment.getId(),
                    e);
            return false;
        }

        try {
            attachments.deleteById(attachment.getId());
            return true;
        } catch (RuntimeException e) {
            // The bytes have gone and the row has not. Harmless and self-healing:
            // the row is still purgeable, and deleting an absent object succeeds.
            log.warn(
                    "Reclaimed the stored object for attachment {} but could not delete its row; "
                            + "the next run will finish the job",
                    attachment.getId(),
                    e);
            return false;
        }
    }

    /** What one run did. A failure is a file still in the store, with its row still naming it. */
    record Result(int reclaimed, int failed) {}
}
