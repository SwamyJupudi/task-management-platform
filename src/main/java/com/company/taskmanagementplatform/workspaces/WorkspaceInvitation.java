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
 * An offer of membership, addressed to an email rather than to an account.
 *
 * <p>Addressing the email is what lets somebody be invited before they have registered, without
 * creating a placeholder account that would then need a status of its own and would show up in every
 * user list.
 *
 * <p>Only the hash of the token is here. The token itself is generated once, sent to the recipient
 * and never stored, so a copy of this table cannot be used to join anything.
 */
@Entity
@Table(name = "workspace_invitations")
class WorkspaceInvitation {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invited_by_user_id", nullable = false)
    private UUID invitedByUserId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by_user_id")
    private UUID acceptedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceInvitation() {
        // for JPA
    }

    static WorkspaceInvitation issue(
            UUID workspaceId, String normalizedEmail, UUID roleId, String tokenHash, Instant expiresAt, UUID invitedBy) {
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.workspaceId = workspaceId;
        invitation.email = normalizedEmail;
        invitation.roleId = roleId;
        invitation.tokenHash = tokenHash;
        invitation.status = InvitationStatus.PENDING;
        invitation.expiresAt = expiresAt;
        invitation.invitedByUserId = invitedBy;
        return invitation;
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

    boolean isRedeemable(Instant now) {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    void accept(UUID userId, Instant now) {
        status = InvitationStatus.ACCEPTED;
        acceptedAt = now;
        acceptedByUserId = userId;
    }

    void revoke() {
        status = InvitationStatus.REVOKED;
    }

    void markExpired() {
        status = InvitationStatus.EXPIRED;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getEmail() {
        return email;
    }

    UUID getRoleId() {
        return roleId;
    }

    InvitationStatus getStatus() {
        return status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
