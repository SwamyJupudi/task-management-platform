package com.company.taskmanagementplatform.teams;

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
 * A group of people inside one workspace, with at most one lead.
 *
 * <p>The lead is a single column because the requirements say assign a team lead, in the singular.
 * It is nullable, because a team between leads is an ordinary state and refusing to represent it
 * would mean either inventing a placeholder or refusing to let a lead leave.
 *
 * <p>The workspace is a plain identifier rather than an association, matching the convention in the
 * {@code workspaces} module: every query here already knows the identifier it wants, so a proxy
 * would buy nothing.
 */
@Entity
@Table(name = "teams")
class Team {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /**
     * Always either null or somebody with a membership row in this team's workspace, which the
     * database enforces through a composite foreign key rather than trusting the service.
     */
    @Column(name = "lead_user_id")
    private UUID leadUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TeamStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Team() {
        // for JPA
    }

    static Team create(UUID workspaceId, String name, String description, UUID createdByUserId) {
        Team team = new Team();
        team.workspaceId = workspaceId;
        team.name = name;
        team.description = description;
        team.status = TeamStatus.ACTIVE;
        team.createdByUserId = createdByUserId;
        return team;
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

    void assignLead(UUID userId) {
        this.leadUserId = userId;
    }

    void clearLead() {
        this.leadUserId = null;
    }

    void archive() {
        this.status = TeamStatus.ARCHIVED;
    }

    void unarchive() {
        this.status = TeamStatus.ACTIVE;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    boolean isArchived() {
        return status == TeamStatus.ARCHIVED;
    }

    boolean isLedBy(UUID userId) {
        return leadUserId != null && leadUserId.equals(userId);
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    UUID getLeadUserId() {
        return leadUserId;
    }

    TeamStatus getStatus() {
        return status;
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
