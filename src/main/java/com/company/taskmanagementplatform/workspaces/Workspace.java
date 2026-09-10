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
 * A workspace: the root scope everything else in the platform hangs from.
 *
 * <p>The slug is deliberately not editable. It appears in links people have already sent each other,
 * and renaming it would break every one of them silently. The display name is what changes.
 *
 * <p>Archiving and deletion are different things and are kept apart. Archiving is reversible and
 * freezes the workspace: its contents stay readable and nothing inside it may be changed. Deletion is
 * a soft delete, which hides the workspace and releases its slug for reuse.
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

    @Column(name = "slug", nullable = false, updatable = false)
    private String slug;

    @Column(name = "description")
    private String description;

    /** An IANA zone name. Validated by the service against the JDK's zone database. */
    @Column(name = "timezone", nullable = false)
    private String timezone;

    /** The role an invitation uses when it does not name one. Always a role of this workspace. */
    @Column(name = "default_role_id")
    private UUID defaultRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkspaceStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by_user_id")
    private UUID archivedByUserId;

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
        workspace.timezone = "UTC";
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

    void rename(String newName) {
        this.name = newName;
    }

    void describe(String newDescription) {
        this.description = newDescription;
    }

    void moveToTimezone(String newTimezone) {
        this.timezone = newTimezone;
    }

    void useDefaultRole(UUID roleId) {
        this.defaultRoleId = roleId;
    }

    void archive(UUID byUserId, Instant now) {
        this.status = WorkspaceStatus.ARCHIVED;
        this.archivedAt = now;
        this.archivedByUserId = byUserId;
    }

    void unarchive() {
        // Both halves, because the schema refuses a status and a timestamp that
        // disagree about whether this workspace is archived.
        this.status = WorkspaceStatus.ACTIVE;
        this.archivedAt = null;
        this.archivedByUserId = null;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    boolean isArchived() {
        return status == WorkspaceStatus.ARCHIVED;
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

    String getDescription() {
        return description;
    }

    String getTimezone() {
        return timezone;
    }

    UUID getDefaultRoleId() {
        return defaultRoleId;
    }

    WorkspaceStatus getStatus() {
        return status;
    }

    Instant getArchivedAt() {
        return archivedAt;
    }

    UUID getCreatedByUserId() {
        return createdByUserId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
