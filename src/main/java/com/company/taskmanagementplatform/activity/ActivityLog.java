package com.company.taskmanagementplatform.activity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One recorded action.
 *
 * <p><strong>Append only.</strong> There is no {@code updated_at}, no {@code deleted_at}, and no
 * mutator on this class beyond the one the constructor performs. The database enforces it as well,
 * with a trigger that refuses an {@code UPDATE} or a {@code DELETE} from any connection, so this is
 * not a rule that depends on the application remembering it. See {@code V7}.
 *
 * <p>The actor is nullable, unlike everywhere else that records who did something. An action taken
 * by the platform rather than by a person has no actor, and inventing a system user to satisfy a
 * constraint would put a fictional person in the audit trail.
 *
 * <p>{@code metadata} holds the values that make a row mean something: which statuses, which
 * assignee, which filename. It is JSON rather than columns because every action carries different
 * facts, and a table with a column per action would be mostly nulls and would need a migration for
 * every new kind of event. The Java side is a {@code String} so that Jackson does the serialising in
 * one place and Hibernate does nothing but pass the text through to a {@code jsonb} column.
 *
 * <p>No prose is stored. "Srikanth assigned Task #123 to Rahul" is composed when the row is read,
 * from identifiers resolved at that moment, so renaming somebody does not leave a stale sentence in
 * the audit trail.
 */
@Entity
@Table(name = "activity_logs")
class ActivityLog {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Null exactly when the action happened outside any workspace.
     *
     * <p>Administering an account and granting the platform role belong to the installation rather
     * than to one workspace, and phase nine is the first thing to record either. {@code V10} dropped
     * the {@code NOT NULL} for them.
     *
     * <p><strong>The invariant that makes this safe:</strong> every workspace-scoped query filters
     * on this column, so a platform row can never appear in a workspace's history, and the platform
     * browse asks for {@code IS NULL}, so a workspace row can never appear in that. Both directions
     * matter and both are asserted.
     */
    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private ActivityEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", updatable = false)
    private String metadata;

    @Column(name = "request_id", updatable = false)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityLog() {
        // for JPA
    }

    ActivityLog(
            UUID workspaceId,
            UUID actorUserId,
            String action,
            ActivityEntityType entityType,
            UUID entityId,
            UUID projectId,
            String metadata,
            String requestId,
            Instant createdAt) {

        this.workspaceId = workspaceId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.projectId = projectId;
        this.metadata = metadata;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    String getAction() {
        return action;
    }

    ActivityEntityType getEntityType() {
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

    String getRequestId() {
        return requestId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
