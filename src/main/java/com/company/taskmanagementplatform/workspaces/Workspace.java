package com.company.taskmanagementplatform.workspaces;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A workspace, with only the columns identity and authorization need.
 *
 * <p>Deliberately thin. Settings, archiving and the rest of the lifecycle belong to the workspace
 * phase; what exists here is what roles and memberships must be able to point at, because neither
 * can reference a table that does not exist.
 */
@Entity
@Table(name = "workspaces")
class Workspace {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkspaceStatus status;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Workspace() {
        // for JPA
    }

    static Workspace create(String name, String slug, UUID createdByUserId) {
        Workspace workspace = new Workspace();
        workspace.name = name;
        workspace.slug = slug;
        workspace.status = WorkspaceStatus.ACTIVE;
        workspace.createdByUserId = createdByUserId;
        return workspace;
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

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getSlug() {
        return slug;
    }

    WorkspaceStatus getStatus() {
        return status;
    }

    UUID getCreatedByUserId() {
        return createdByUserId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
