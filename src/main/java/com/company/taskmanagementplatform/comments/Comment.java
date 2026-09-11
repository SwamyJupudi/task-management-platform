package com.company.taskmanagementplatform.comments;

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
 * One remark on a task.
 *
 * <p>Flat, with no parent. The requirements list add, edit, delete, mentions and comment activity,
 * and describe no replies; a thread column would bring ordering and depth rules nothing asked for.
 *
 * <p>The author is a plain reference to a user rather than to a membership row, which is the
 * opposite of how a task keys its assignee, and the difference is deliberate. A comment has to
 * outlive its author leaving the workspace: deleting somebody's words because they changed team
 * would destroy the discussion the requirements ask us to keep. Because {@code users} rows are only
 * ever soft-deleted, the key stays valid forever and this module needs no cleanup listener when
 * somebody is removed from a workspace.
 *
 * <p>{@code editedAt} is separate from {@code updatedAt} on purpose. A reader deserves to know when
 * the words changed; {@code updatedAt} moves for reasons that are not edits, so it cannot say.
 *
 * <p>The body is text and is never HTML. Escaping and any markdown rendering belong to whatever
 * displays it, and storing markup would make this table the place an injected script lives.
 */
@Entity
@Table(name = "comments")
class Comment {

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

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Comment() {
        // for JPA
    }

    static Comment create(UUID workspaceId, UUID projectId, UUID taskId, UUID authorUserId, String body) {
        Comment comment = new Comment();
        comment.workspaceId = workspaceId;
        comment.projectId = projectId;
        comment.taskId = taskId;
        comment.authorUserId = authorUserId;
        comment.body = body;
        return comment;
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

    /** The one path that rewrites the body, so the edit stamp cannot be forgotten. */
    void editBody(String newBody, Instant now) {
        this.body = newBody;
        this.editedAt = now;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    boolean isWrittenBy(UUID userId) {
        return authorUserId.equals(userId);
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

    UUID getAuthorUserId() {
        return authorUserId;
    }

    String getBody() {
        return body;
    }

    Instant getEditedAt() {
        return editedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
