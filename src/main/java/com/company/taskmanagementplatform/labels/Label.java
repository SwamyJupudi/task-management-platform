package com.company.taskmanagementplatform.labels;

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
 * One tag in a workspace's catalog.
 *
 * <p>One catalog serves projects and tasks both. The requirements call them tags on a project and
 * labels on a task, but they are the same thing used twice, and two near-identical tables would earn
 * nothing beyond a second place to keep in step.
 *
 * <p>The catalog moved into its own package when tasks arrived. It had lived in {@code projects}
 * while projects were its only user, and leaving it there would have meant either {@code tasks}
 * reaching into another module's tables or a second copy of the folding rules. Neither is worth it
 * for a table both modules genuinely share.
 *
 * <p>The class is public because {@code projects} and {@code tasks} each name it in a criteria
 * subquery when filtering by tag. Its accessors are not: the catalog is read and written through
 * {@link LabelCatalog}.
 *
 * <p>There is no label administration API in this phase. Tagging get-or-creates the label by folded
 * name, which is enough to make tags work and leaves the catalog screen to the admin panel. Labels
 * are not soft deleted: a tag nobody uses is not worth restoring.
 */
@Entity
@Table(name = "labels")
public class Label {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Label() {
        // for JPA
    }

    static Label create(UUID workspaceId, String name) {
        Label label = new Label();
        label.workspaceId = workspaceId;
        label.name = name;
        return label;
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

    String getName() {
        return name;
    }
}
