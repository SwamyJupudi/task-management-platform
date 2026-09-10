package com.company.taskmanagementplatform.projects;

import java.time.Instant;
import java.time.LocalDate;
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
 * A body of work inside one workspace.
 *
 * <p>The owner and the team are plain identifiers rather than associations, matching the convention
 * across the other modules: every query here already knows the identifier it wants. Both are
 * nullable and both are pinned to this workspace by a composite foreign key, so an owner from
 * outside the workspace or a team from another one are writes the database refuses.
 *
 * <p>The key is the short handle the project is known by, uppercase and unique within its workspace.
 * Phase five numbers tasks from it.
 *
 * <p>Progress is stored and not maintained here. The rule that derives it from tasks needs tasks,
 * which arrive in phase five; until then it is zero, and nothing in this module writes it.
 */
@Entity
@Table(name = "projects")
class Project {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "team_id")
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private ProjectPriority priority;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Derived from tasks in phase five. Zero and untouched in this one. */
    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Project() {
        // for JPA
    }

    static Project create(UUID workspaceId, String key, String name, UUID createdByUserId) {
        Project project = new Project();
        project.workspaceId = workspaceId;
        project.key = key;
        project.name = name;
        project.status = ProjectStatus.PLANNING;
        project.priority = ProjectPriority.MEDIUM;
        project.progress = 0;
        project.createdByUserId = createdByUserId;
        return project;
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

    void assignOwner(UUID userId) {
        this.ownerUserId = userId;
    }

    void clearOwner() {
        this.ownerUserId = null;
    }

    void assignTeam(UUID newTeamId) {
        this.teamId = newTeamId;
    }

    void clearTeam() {
        this.teamId = null;
    }

    void reprioritise(ProjectPriority newPriority) {
        this.priority = newPriority;
    }

    void schedule(LocalDate newStart, LocalDate newEnd) {
        this.startDate = newStart;
        this.endDate = newEnd;
    }

    void moveTo(ProjectStatus target) {
        this.status = target;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }

    boolean isOwnedBy(UUID userId) {
        return ownerUserId != null && ownerUserId.equals(userId);
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getKey() {
        return key;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    UUID getOwnerUserId() {
        return ownerUserId;
    }

    UUID getTeamId() {
        return teamId;
    }

    ProjectStatus getStatus() {
        return status;
    }

    ProjectPriority getPriority() {
        return priority;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    int getProgress() {
        return progress;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
