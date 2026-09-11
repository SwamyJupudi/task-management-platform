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

    protected Attachment() {
        // for JPA
    }

    static Attachment create(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID uploaderUserId,
            String filename,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            String storageProvider,
            String storageKey) {

        Attachment attachment = new Attachment();
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

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
