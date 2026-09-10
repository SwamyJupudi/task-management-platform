package com.company.taskmanagementplatform.workspaces;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One capability the application implements, named {@code resource:action}.
 *
 * <p>The catalog is global rather than per workspace, because a permission code names something the
 * code can actually do. Only the mapping from role to permission varies by workspace. Rows are
 * written by migrations, never by the application, and the constants in {@code Permissions} are the
 * compile-time half of the same list.
 */
@Entity
@Table(name = "permissions")
class Permission {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "resource", nullable = false, updatable = false)
    private String resource;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Permission() {
        // for JPA
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getResource() {
        return resource;
    }

    String getAction() {
        return action;
    }

    String getDescription() {
        return description;
    }
}
