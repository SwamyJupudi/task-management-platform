package com.company.taskmanagementplatform.workspaces;

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
 * One person's place in one workspace, with exactly one role.
 *
 * <p>The workspace identifier is stored alongside the role identifier, and that redundancy is
 * load-bearing rather than sloppy. The two together are a foreign key into {@code roles (id,
 * workspace_id)}, so the database itself refuses a role from another workspace, and refuses a
 * platform role outright because its workspace is null.
 */
@Entity
@Table(name = "workspace_members")
class WorkspaceMember {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceMember() {
        // for JPA
    }

    static WorkspaceMember join(UUID workspaceId, UUID userId, UUID roleId, UUID invitedByUserId, Instant now) {
        WorkspaceMember member = new WorkspaceMember();
        member.workspaceId = workspaceId;
        member.userId = userId;
        member.roleId = roleId;
        member.invitedByUserId = invitedByUserId;
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

    void changeRole(UUID newRoleId) {
        this.roleId = newRoleId;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getRoleId() {
        return roleId;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }
}
