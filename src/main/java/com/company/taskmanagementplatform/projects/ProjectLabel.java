package com.company.taskmanagementplatform.projects;

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
 * The tagging of one project with one label.
 *
 * <p>Carries the workspace so that both halves are keyed to it: the project through {@code projects
 * (id, workspace_id)} and the label through {@code labels (id, workspace_id)}. Tagging a project
 * with another workspace's label is therefore not expressible.
 */
@Entity
@Table(name = "project_labels")
class ProjectLabel {

    @EmbeddedId
    private ProjectLabelId id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectLabel() {
        // for JPA
    }

    ProjectLabel(UUID projectId, UUID labelId, UUID workspaceId) {
        this.id = new ProjectLabelId(projectId, labelId);
        this.workspaceId = workspaceId;
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
    }

    ProjectLabelId getId() {
        return id;
    }

    /** Composite key, matching the table's primary key over the two identifiers. */
    @Embeddable
    static class ProjectLabelId implements Serializable {

        @Column(name = "project_id", nullable = false)
        private UUID projectId;

        @Column(name = "label_id", nullable = false)
        private UUID labelId;

        protected ProjectLabelId() {
            // for JPA
        }

        ProjectLabelId(UUID projectId, UUID labelId) {
            this.projectId = projectId;
            this.labelId = labelId;
        }

        UUID getProjectId() {
            return projectId;
        }

        UUID getLabelId() {
            return labelId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProjectLabelId that)) {
                return false;
            }
            return Objects.equals(projectId, that.projectId) && Objects.equals(labelId, that.labelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, labelId);
        }
    }
}
