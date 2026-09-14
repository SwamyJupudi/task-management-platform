package com.company.taskmanagementplatform.attachments;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.scheduling.AdvisoryLock;
import com.company.taskmanagementplatform.common.scheduling.LockKeys;

/**
 * Scans the attachments no scanner has ever looked at, and promotes the clean ones.
 *
 * <p>This is the other half of a decision made in {@code V12__attachment_scan_state.sql}. That migration
 * records every pre-existing attachment as {@code PENDING} rather than backfilling it to {@code CLEAN},
 * because nothing had inspected those files; the honest consequence is that they stop being downloadable, and
 * this job is the only thing that makes them downloadable again. Without it the migration would be a one-way
 * door.
 *
 * <p><strong>Only a clean verdict promotes a file, and the verdict has to be about the file's own bytes.</strong>
 * Each attachment is read back out of the object store and sent to the scanner as it actually exists — not
 * assumed clean because it is old, not assumed clean because it passed the content-type allowlist when it was
 * uploaded. That is the whole point: the upload-time checks are not a scan, and pretending otherwise is what
 * the migration refuses to do.
 *
 * <p>Three outcomes per file, and each one leaves the row in a state the download guard already understands:
 *
 * <ul>
 *   <li><strong>Clean</strong> — {@code CLEAN}, with the moment of the scan recorded, and the file becomes
 *       downloadable.
 *   <li><strong>Infected</strong> — {@code REJECTED} with the signature. Still not downloadable, and now with
 *       a record of why. The bytes are left in the store: deleting somebody's file from a background job is a
 *       larger decision than recording a verdict, and the byte purge exists for when an operator makes it.
 *   <li><strong>The scanner could not say</strong> — nothing is written. The row stays {@code PENDING} and the
 *       next run tries again. This is the fail-closed direction: an unreachable scanner must never be the
 *       reason a file becomes servable, which is exactly what a "if we cannot scan it, assume it is fine"
 *       branch would do. A whole batch of these stops the run, because it points at the scanner rather than at
 *       the files.
 * </ul>
 *
 * <p><strong>One file per transaction, and each transaction opens after the scan.</strong> The scan is a
 * network call to a scanner and a read from an object store; holding a database transaction across those for a
 * whole batch would tie up a connection for the duration of a batch of external calls. A verdict per row also
 * means a run that dies half way has committed every verdict it reached.
 *
 * <p><strong>Nothing here runs inside a request.</strong> The scheduler and the tests are the only entry
 * points, and each write opens its own {@code REQUIRES_NEW} transaction.
 *
 * <p>One instance at a time, by the same advisory lock the other three jobs use. Overlapping runs would send
 * the same files to the scanner twice, which is wasteful rather than wrong, and would be paid for in egress.
 */
@Component
class AttachmentRescan {

    private static final Logger log = LoggerFactory.getLogger(AttachmentRescan.class);

    /**
     * The most batches one run will do.
     *
     * <p>A stop rather than a target, for the reason the purges give. A backlog larger than this waits for the
     * next hour, which on a populated installation is how it is going to be worked through anyway.
     */
    private static final int MAX_BATCHES = 20;

    private final AttachmentRepository attachments;
    private final FileStore store;
    private final MalwareScanner scanner;
    private final AttachmentRescan self;
    private final AdvisoryLock lock;
    private final AttachmentRescanProperties properties;
    private final Clock clock;

    /**
     * @param self this bean through its own proxy, so the per-file transaction below is honoured. A call from
     *     one method to another on {@code this} does not pass through the proxy and would silently run without
     *     it. Lazy, so the self-reference is not a startup failure
     */
    AttachmentRescan(
            AttachmentRepository attachments,
            FileStore store,
            MalwareScanner scanner,
            @org.springframework.context.annotation.Lazy AttachmentRescan self,
            AdvisoryLock lock,
            AttachmentRescanProperties properties,
            Clock clock) {
        this.attachments = attachments;
        this.store = store;
        this.scanner = scanner;
        this.self = self;
        this.lock = lock;
        this.properties = properties;
        this.clock = clock;
    }

    /** The scheduled entry point, which does nothing unless the rescan was switched on. */
    @Scheduled(cron = "${app.storage.rescan.cron}")
    void scheduled() {
        if (!properties.enabled()) {
            return;
        }

        try {
            run();
        } catch (RuntimeException e) {
            log.error("The attachment rescan failed", e);
        }
    }

    /**
     * Rescans once, if this instance gets the lock.
     *
     * @return what the run concluded, and zeroes when another instance holds the lock
     */
    Result run() {
        int[] counts = new int[3];

        boolean ran = lock.runExclusively(LockKeys.ATTACHMENT_RESCAN, () -> {
            Result result = rescan();
            counts[0] = result.cleared();
            counts[1] = result.rejected();
            counts[2] = result.unresolved();
        });

        if (!ran) {
            return new Result(0, 0, 0);
        }

        Result result = new Result(counts[0], counts[1], counts[2]);
        if (result.rejected() > 0 || result.unresolved() > 0) {
            log.warn(
                    "Attachment rescan complete: cleared={} rejected={} unresolved={}",
                    result.cleared(),
                    result.rejected(),
                    result.unresolved());
        } else if (result.cleared() > 0) {
            log.info("Attachment rescan complete: cleared={}", result.cleared());
        }
        return result;
    }

    /**
     * Walks the unscanned backlog, oldest upload first.
     *
     * <p>Each pass re-reads the first page rather than paging forward by number, because a promotion removes a
     * row from the result set and would shift every later page under its own feet. The rows this run could not
     * resolve stay in the set, so they are held here and skipped; that is also how the run knows it has stopped
     * making progress.
     */
    private Result rescan() {
        int batchSize = properties.batchSize();
        java.util.Set<UUID> unresolved = new java.util.HashSet<>();
        int cleared = 0;
        int rejected = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Attachment> due = attachments.findUnscanned(PageRequest.of(0, batchSize + unresolved.size()));

            List<Attachment> pending = due.stream()
                    .filter(attachment -> !unresolved.contains(attachment.getId()))
                    // A rejection has already been looked at. The query deliberately
                    // asks for everything that is not CLEAN so that a new state is
                    // re-checked rather than silently skipped; this is where the ones
                    // already resolved are dropped.
                    .filter(attachment -> !ScanStatus.REJECTED.equals(attachment.getScanStatus()))
                    .toList();

            if (pending.isEmpty()) {
                return new Result(cleared, rejected, unresolved.size());
            }

            for (Attachment attachment : pending) {
                Outcome outcome = rescan(attachment);
                switch (outcome) {
                    case CLEARED -> cleared++;
                    case REJECTED -> rejected++;
                    case UNRESOLVED -> unresolved.add(attachment.getId());
                }
            }

            // A whole batch that could not be resolved points at the scanner or the
            // object store rather than at the files, and sending the rest of the
            // backlog at an unreachable scanner would achieve nothing.
            if (unresolved.size() >= batchSize) {
                log.warn(
                        "The attachment rescan stopped after {} files it could not resolve, which points at "
                                + "the scanner or the object store rather than the files. cleared={} rejected={}",
                        unresolved.size(),
                        cleared,
                        rejected);
                return new Result(cleared, rejected, unresolved.size());
            }
        }

        log.warn(
                "The attachment rescan stopped at its batch limit with files still unscanned; the next run "
                        + "will continue. cleared={} rejected={} unresolved={}",
                cleared,
                rejected,
                unresolved.size());
        return new Result(cleared, rejected, unresolved.size());
    }

    /** Reads one file, scans it, and records the verdict. */
    private Outcome rescan(Attachment attachment) {
        byte[] content;
        try (InputStream stream = store.open(attachment.getStorageKey())) {
            content = stream.readAllBytes();
        } catch (IOException | RuntimeException e) {
            // The row is the only thing that knows the key, so it stays. A missing
            // object is not a reason to make a file servable.
            log.warn(
                    "Could not read attachment {} out of the store to scan it; leaving it unscanned",
                    attachment.getId(),
                    e);
            return Outcome.UNRESOLVED;
        }

        ScanVerdict verdict = scanner.scan(content);
        Instant scannedAt = clock.instant();

        if (verdict.isClean()) {
            self.recordClean(attachment.getId(), scannedAt);
            return Outcome.CLEARED;
        }

        if (verdict.isInfected()) {
            // The signature, never the filename and never any of the content.
            log.warn(
                    "Malware found in an existing attachment during rescan: attachmentId={} signature={} "
                            + "scanner={}",
                    attachment.getId(),
                    verdict.detail(),
                    scanner.provider());
            self.recordRejected(attachment.getId(), verdict.detail(), scannedAt);
            return Outcome.REJECTED;
        }

        log.warn(
                "Could not scan attachment {}, so it stays unscanned and undownloadable: reason={} scanner={}",
                attachment.getId(),
                verdict.detail(),
                scanner.provider());
        return Outcome.UNRESOLVED;
    }

    /**
     * Promotes one file, in a transaction of its own.
     *
     * <p>Re-read inside the transaction rather than reusing the detached instance from the batch, so the write
     * applies to the row as it is now. Package-private because a private method cannot be proxied and the
     * propagation would be quietly ignored.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordClean(UUID attachmentId, Instant scannedAt) {
        attachments.findById(attachmentId).ifPresent(attachment -> attachment.markScanClean(scannedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordRejected(UUID attachmentId, String signature, Instant scannedAt) {
        attachments
                .findById(attachmentId)
                .ifPresent(attachment -> attachment.markScanRejected(signature, scannedAt));
    }

    /** What happened to one file. */
    private enum Outcome {
        CLEARED,
        REJECTED,
        UNRESOLVED
    }

    /**
     * What one run concluded.
     *
     * @param cleared files a scanner passed, which are now downloadable
     * @param rejected files a scanner refused, which are not and will not be
     * @param unresolved files that could not be scanned at all, still unscanned and still blocked
     */
    record Result(int cleared, int rejected, int unresolved) {}
}
