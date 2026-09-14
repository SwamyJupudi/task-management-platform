package com.company.taskmanagementplatform.attachments;

/**
 * The values {@code attachments.scan_status} allows, so the column and the code cannot drift.
 *
 * <p>Constants rather than an enum, matching how {@code Attachment} stores every other status: the column is
 * text with a check constraint, and an enum here would invite a mapping that has to be kept in step with the
 * constraint as well as with the column.
 *
 * <p>{@link #PENDING} is where every attachment that predates {@code V12} starts, and it is where they stay
 * until {@code AttachmentRescan} has read them out of the store and had a scanner look at them. The upload
 * path never writes it — that scans before it stores, so a file which cannot be cleared never becomes a row —
 * and {@link #SCANNING} is reserved for an asynchronous engine. {@code V12__attachment_scan_state.sql} sets
 * out the whole lifecycle and what the migration costs an installation with an existing backlog.
 */
final class ScanStatus {

    /**
     * Not inspected. Not downloadable.
     *
     * <p>Every row that existed before scanning did is in this state, which is the honest record of the fact
     * that nothing has looked at those files. The rescan is what moves them out of it.
     */
    static final String PENDING = "PENDING";

    /** An asynchronous engine has it. Not downloadable. */
    static final String SCANNING = "SCANNING";

    /**
     * Inspected and passed. The only state a download is served from.
     *
     * <p>Reachable only from a successful scanner verdict, in exactly two places: an upload whose scan passed,
     * and the rescan promoting a file it has just had cleared. Both stamp {@code scanned_at}, and a check
     * constraint refuses this status without one.
     */
    static final String CLEAN = "CLEAN";

    /**
     * Something was found. Not downloadable, and kept so the record of it survives.
     *
     * <p>Written by the rescan. An infected upload never reaches a row, because it is refused before anything
     * is stored, so these rows describe a file that predates scanning or predates the signature that caught
     * it.
     */
    static final String REJECTED = "REJECTED";

    private ScanStatus() {}
}
