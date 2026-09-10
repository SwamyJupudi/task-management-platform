package com.company.taskmanagementplatform.tasks;

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
 * The labelling of one task with one label.
 *
 * <p>The mirror of {@code ProjectLabel}, over the same catalog. It carries the workspace so that
 * both halves are keyed to it: the task through {@code tasks (id, workspace_id)} and the label
 * through {@code labels (id, workspace_id)}. Labelling a task with another workspace's label is
 * therefore not expressible.
 */
@Entity
@Table(name = "task_labels")
class TaskLabel {

    @EmbeddedId
    private TaskLabelId id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskLabel() {
        // for JPA
    }

    TaskLabel(UUID taskId, UUID labelId, UUID workspaceId) {
        this.id = new TaskLabelId(taskId, labelId);
        this.workspaceId = workspaceId;
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
    }

    /** Composite key, matching the table's primary key over the two identifiers. */
    @Embeddable
    static class TaskLabelId implements Serializable {

        @Column(name = "task_id", nullable = false)
        private UUID taskId;

        @Column(name = "label_id", nullable = false)
        private UUID labelId;

        protected TaskLabelId() {
            // for JPA
        }

        TaskLabelId(UUID taskId, UUID labelId) {
            this.taskId = taskId;
            this.labelId = labelId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TaskLabelId that)) {
                return false;
            }
            return Objects.equals(taskId, that.taskId) && Objects.equals(labelId, that.labelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, labelId);
        }
    }
}
