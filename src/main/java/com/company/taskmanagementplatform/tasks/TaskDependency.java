package com.company.taskmanagementplatform.tasks;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One task waiting on another. The row reads: {@code taskId} is blocked by {@code dependsOnTaskId}.
 *
 * <p>There is no type column. The requirements name TaskDependency as an entity and say nothing
 * about kinds of dependency, so this is a single blocking relationship, as {@code architecture.md}
 * records. Adding BLOCKS, RELATES and DUPLICATES would mean inventing semantics for each of them and
 * then implementing three rules where the requirements asked for none.
 *
 * <p>Both ends are keyed to the same project by the database, which makes a cross-workspace
 * dependency unrepresentable and closes an information leak at the same time: a dependency reaching
 * into another project would render a blocker's identifier to somebody who cannot see the project it
 * lives in.
 *
 * <p>A join table, so it is never soft deleted. Soft deleting either end removes the rows touching
 * it, which keeps the cycle walk from traversing tasks that no read can see.
 */
@Entity
@Table(name = "task_dependencies")
class TaskDependency {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "depends_on_task_id", nullable = false, updatable = false)
    private UUID dependsOnTaskId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskDependency() {
        // for JPA
    }

    static TaskDependency of(
            UUID workspaceId, UUID projectId, UUID taskId, UUID dependsOnTaskId, UUID createdByUserId) {
        TaskDependency dependency = new TaskDependency();
        dependency.workspaceId = workspaceId;
        dependency.projectId = projectId;
        dependency.taskId = taskId;
        dependency.dependsOnTaskId = dependsOnTaskId;
        dependency.createdByUserId = createdByUserId;
        return dependency;
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
    }

    UUID getTaskId() {
        return taskId;
    }

    UUID getDependsOnTaskId() {
        return dependsOnTaskId;
    }
}
