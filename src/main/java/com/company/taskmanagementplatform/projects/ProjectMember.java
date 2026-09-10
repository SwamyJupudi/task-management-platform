package com.company.taskmanagementplatform.projects;

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
 * One person's place on one project.
 *
 * <p>There is deliberately no role column. The requirements describe no project-level role, and
 * adding one would be a second authorization model sitting beside the workspace roles, with its own
 * resolution path that no test of the first one covers.
 *
 * <p>The workspace identifier is stored beside the project and the user, and carries the same two
 * rules {@code team_members} carries: paired with the project it keys into {@code projects (id,
 * workspace_id)}, and paired with the user into {@code workspace_members (workspace_id, user_id)}.
 * Both are writes the database refuses rather than checks a service is trusted to remember.
 *
 * <p>No soft deletion, following the convention that it never applies to a join table.
 */
@Entity
@Table(name = "project_members")
class ProjectMember {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "added_by_user_id")
    private UUID addedByUserId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectMember() {
        // for JPA
    }

    static ProjectMember join(UUID projectId, UUID workspaceId, UUID userId, UUID addedByUserId, Instant now) {
        ProjectMember member = new ProjectMember();
        member.projectId = projectId;
        member.workspaceId = workspaceId;
        member.userId = userId;
        member.addedByUserId = addedByUserId;
        member.joinedAt = now;
        return member;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (joinedAt == null) {
            joinedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    UUID getProjectId() {
        return projectId;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }
}
