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
 * A named set of permissions, either platform-wide or belonging to one workspace.
 *
 * <p>The workspace is a plain identifier rather than an association. Associations across this module
 * would buy lazy loading and cost a tangle of proxies in a place where every query already knows the
 * identifier it wants.
 */
@Entity
@Table(name = "roles")
class Role {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Null exactly when the scope is platform, which the table's check constraint enforces. */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private RoleScope scope;

    /** A seeded role. The admin panel may edit its permissions but never delete it. */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Role() {
        // for JPA
    }

    static Role workspaceRole(UUID workspaceId, SystemRole template) {
        Role role = new Role();
        role.workspaceId = workspaceId;
        role.slug = template.slug();
        role.name = template.displayName();
        role.scope = RoleScope.WORKSPACE;
        role.system = true;
        return role;
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

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getSlug() {
        return slug;
    }

    String getName() {
        return name;
    }

    RoleScope getScope() {
        return scope;
    }

    boolean isSystem() {
        return system;
    }
}
