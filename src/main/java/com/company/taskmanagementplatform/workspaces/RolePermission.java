package com.company.taskmanagementplatform.workspaces;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * The grant of one permission to one role.
 *
 * <p>This is the table the admin panel will edit, and the one that gives {@code SUPER_ADMIN} its
 * reach. There is no shortcut anywhere in the authorization path for the platform administrator: it
 * holds every permission because a migration wrote a row here for each one.
 */
@Entity
@Table(name = "role_permissions")
class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RolePermission() {
        // for JPA
    }

    RolePermission(UUID roleId, UUID permissionId) {
        this.id = new RolePermissionId(roleId, permissionId);
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
    }

    RolePermissionId getId() {
        return id;
    }

    /** Composite key, matching the table's primary key over the two identifiers. */
    @Embeddable
    static class RolePermissionId implements Serializable {

        @Column(name = "role_id", nullable = false)
        private UUID roleId;

        @Column(name = "permission_id", nullable = false)
        private UUID permissionId;

        protected RolePermissionId() {
            // for JPA
        }

        RolePermissionId(UUID roleId, UUID permissionId) {
            this.roleId = roleId;
            this.permissionId = permissionId;
        }

        UUID getRoleId() {
            return roleId;
        }

        UUID getPermissionId() {
            return permissionId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RolePermissionId that)) {
                return false;
            }
            return Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, permissionId);
        }
    }
}
