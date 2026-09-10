package com.company.taskmanagementplatform.teams;

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
 * One person's place in one team.
 *
 * <p>The workspace identifier is stored beside the team and the user, and the redundancy is
 * load-bearing in two directions at once. Paired with the team it keys into {@code teams (id,
 * workspace_id)}, so a row cannot record a team from another workspace. Paired with the user it keys
 * into {@code workspace_members (workspace_id, user_id)}, so somebody who does not belong to the
 * workspace cannot be put in one of its teams. Both are writes the database refuses rather than
 * rules a service is trusted to remember.
 *
 * <p>No soft deletion, following the convention that it never applies to a join table.
 */
@Entity
@Table(name = "team_members")
class TeamMember {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

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

    protected TeamMember() {
        // for JPA
    }

    static TeamMember join(UUID teamId, UUID workspaceId, UUID userId, UUID addedByUserId, Instant now) {
        TeamMember member = new TeamMember();
        member.teamId = teamId;
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

    UUID getId() {
        return id;
    }

    UUID getTeamId() {
        return teamId;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }
}
