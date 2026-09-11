package com.company.taskmanagementplatform.notifications;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One thing somebody is told.
 *
 * <p>This class is read-mostly. Rows are written by a native insert in {@link NotificationWriter},
 * which is what lets a duplicate deadline row be skipped by the database instead of being checked
 * for by the application, and the only field anything mutates afterwards is {@link #readAt}. There
 * is deliberately no constructor taking every column: nothing outside this package may write one,
 * and nothing inside it needs to.
 *
 * <p>{@code type} and {@code entityType} are strings here rather than enums. The stored value is the
 * dotted code, the check constraint in {@code V8} is the authority on which codes exist, and a row
 * carrying a code this build does not know about should render as a plain line rather than break a
 * whole page with an unmappable value. That is a real case during a rolling deploy.
 *
 * <p>{@code metadata} holds the facts that make a row mean something. It is JSON rather than columns
 * for the reason the audit trail gives: every type carries different facts, and a column per type
 * would be mostly nulls and would need a migration for each new one. No prose is stored; the
 * sentence is composed when the row is read.
 */
@Entity
@Table(name = "notifications")
class Notification {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "type", nullable = false, updatable = false)
    private String type;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", updatable = false)
    private String metadata;

    @Column(name = "dedupe_key", updatable = false)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Notification() {
        // for JPA
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getRecipientUserId() {
        return recipientUserId;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    String getType() {
        return type;
    }

    String getEntityType() {
        return entityType;
    }

    UUID getEntityId() {
        return entityId;
    }

    UUID getProjectId() {
        return projectId;
    }

    String getMetadata() {
        return metadata;
    }

    String getDedupeKey() {
        return dedupeKey;
    }

    Instant getReadAt() {
        return readAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Marks it read, once.
     *
     * <p>A second call changes nothing and reports so, which is what makes the endpoint idempotent
     * without the controller having to compare timestamps. Re-reading a notification does not move
     * the moment it was first read.
     *
     * @return whether this call was the one that moved it
     */
    boolean markRead(Instant now) {
        if (readAt != null) {
            return false;
        }
        readAt = now;
        updatedAt = now;
        return true;
    }
}
