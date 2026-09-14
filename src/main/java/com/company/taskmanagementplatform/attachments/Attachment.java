package com.company.taskmanagementplatform.attachments;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One file held against a task, and optionally against a comment on it.
 *
 * <p>Both keys are columns rather than a polymorphic owner pair, because a polymorphic key cannot be
 * a foreign key and would move referential integrity into the service layer, which is not where the
 * rest of this schema keeps it. The database refuses a file whose comment belongs to a different
 * task.
 *
 * <p>{@code contentType} is what the application detected from the leading bytes. What the client
 * claimed is not stored anywhere, because it is not evidence of anything.
 *
 * <p>{@code storageProvider} and {@code storageKey} are what keep a change of provider a data
 * migration rather than a schema one. The key is opaque and never contains the uploaded filename.
 *
 * <p>The uploader is keyed to {@code users} rather than to a membership row, for the reason {@code
 * Comment} gives: a file has to outlive the person who added it leaving the workspace.
 */
@Entity
@Table(name = "attachments")
class Attachment {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "comment_id")
    private UUID commentId;

    @Column(name = "uploader_user_id", nullable = false, updatable = false)
    private UUID uploaderUserId;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, updatable = false)
    private String checksumSha256;

    @Column(name = "storage_provider", nullable = false, updatable = false)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * What the malware scanner concluded. {@code CLEAN} is the only value a download is served from.
     *
     * <p>Not an enum type on the column, matching every other status in this schema: they are text with a
     * check constraint, so adding a value is a migration rather than a PostgreSQL type alteration.
     */
    @Column(name = "scan_status", nullable = false)
    private String scanStatus;

    /** The engine's own name for what it found. Only ever set on a rejection; the schema enforces that. */
    @Column(name = "scan_signature")
    private String scanSignature;

    /** When an engine actually looked. Null on the rows V12 backfilled, which predate scanning. */
    @Column(name = "scanned_at")
    private Instant scannedAt;

    protected Attachment() {
        // for JPA
    }

    /**
     * A file that has been scanned and passed.
     *
     * <p>There is no factory for one that has not. The upload path scans before it stores anything, so a
     * file that could not be cleared never reaches a row — which is why {@code PENDING} and {@code
     * SCANNING} exist in the schema for an asynchronous engine rather than being reachable from here.
     * Making the only constructor the clean one means an unscanned row cannot be created by forgetting a
     * step.
     *
     * @param scannedAt when the engine looked. Carried in rather than defaulted, so the row records the
     *     moment of the scan rather than the moment of the insert
     */
    static Attachment createScanned(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID uploaderUserId,
            String filename,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            String storageProvider,
            String storageKey,
            Instant scannedAt) {

        Attachment attachment = new Attachment();
        attachment.scanStatus = ScanStatus.CLEAN;
        attachment.scannedAt = scannedAt;
        attachment.workspaceId = workspaceId;
        attachment.projectId = projectId;
        attachment.taskId = taskId;
        attachment.uploaderUserId = uploaderUserId;
        attachment.filename = filename;
        attachment.contentType = contentType;
        attachment.sizeBytes = sizeBytes;
        attachment.checksumSha256 = checksumSha256;
        attachment.storageProvider = storageProvider;
        attachment.storageKey = storageKey;
        return attachment;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Claimed by a comment at the moment that comment is written, and never moved again. */
    void attachTo(UUID commentId) {
        this.commentId = commentId;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    /**
     * Promotes a file to downloadable, because a scanner has just looked at these bytes and passed them.
     *
     * <p>The only way a row becomes {@code CLEAN} other than being created that way by an upload whose scan
     * passed. Both paths set {@code scannedAt} in the same breath as the status, which is what makes a
     * {@code CLEAN} row with no scan timestamp something this application cannot produce — and the database
     * refuses it outright through {@code attachments_clean_requires_scanned_at_check}, so a stray UPDATE
     * cannot produce one either.
     *
     * <p>Any previous signature is cleared. A row reaching this state has no finding against it, and the
     * schema refuses a signature on anything but a rejection.
     */
    void markScanClean(Instant scannedAt) {
        this.scanStatus = ScanStatus.CLEAN;
        this.scanSignature = null;
        this.scannedAt = scannedAt;
    }

    /**
     * Records that a scanner found something, which leaves the file permanently unservable.
     *
     * <p>Reached only by the rescan: an infected upload is refused before anything is stored, so it never
     * becomes a row at all. These rows therefore describe one situation — a file that predates scanning, or
     * predates the signature that catches it, and has now been looked at.
     *
     * <p>The bytes are deliberately left in the store. Deleting somebody's file from a background job is a
     * larger decision than recording a verdict, the row is already unservable, and the signature is worth
     * keeping for whoever investigates. Reclaiming the bytes is the ordinary delete followed by the byte
     * purge, which is an operator's call.
     */
    void markScanRejected(String signature, Instant scannedAt) {
        this.scanStatus = ScanStatus.REJECTED;
        this.scanSignature = signature;
        this.scannedAt = scannedAt;
    }

    /** Whether a scanner has ever looked at these bytes. False for every row that predates {@code V12}. */
    boolean hasBeenScanned() {
        return scannedAt != null;
    }

    boolean isUploadedBy(UUID userId) {
        return uploaderUserId.equals(userId);
    }

    boolean belongsToAComment() {
        return commentId != null;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getProjectId() {
        return projectId;
    }

    UUID getTaskId() {
        return taskId;
    }

    UUID getCommentId() {
        return commentId;
    }

    UUID getUploaderUserId() {
        return uploaderUserId;
    }

    String getFilename() {
        return filename;
    }

    String getContentType() {
        return contentType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getChecksumSha256() {
        return checksumSha256;
    }

    String getStorageProvider() {
        return storageProvider;
    }

    String getStorageKey() {
        return storageKey;
    }

    /**
     * Whether the bytes may be served.
     *
     * <p>Written as "is it clean" rather than "is it not rejected" on purpose. A status added later — an
     * asynchronous engine's {@code SCANNING}, a quarantine state nobody has thought of yet — is refused by
     * this method by default instead of being served by default, which is the same way round the security
     * chain is built and for the same reason.
     */
    boolean isApprovedForDownload() {
        return ScanStatus.CLEAN.equals(scanStatus);
    }

    String getScanStatus() {
        return scanStatus;
    }

    /**
     * The engine's name for what was found, on a row that records a rejection.
     *
     * <p>Always null on a row this application wrote, because an infected upload is refused before anything
     * is stored and so never becomes a row at all. The column and this accessor exist for the rejection an
     * operator records after the fact — quarantining a file a later signature update flagged — which is the
     * case {@code V12__attachment_scan_state.sql} describes.
     */
    String getScanSignature() {
        return scanSignature;
    }

    Instant getScannedAt() {
        return scannedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
