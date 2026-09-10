package com.company.taskmanagementplatform.subtasks;

import java.time.Instant;
import java.time.LocalDate;
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

import com.company.taskmanagementplatform.tasks.TaskStatus;

/**
 * One item on a task's checklist.
 *
 * <p>A table of its own rather than a self-referencing task, because the requirements list SubTask as
 * its own entity and give it a narrower field set: completion, assignee, status and due date. A task
 * that could be its own parent would also need a rule about how deep that goes, which the
 * requirements do not ask for.
 *
 * <p>The requirements name completion and status both, and they are one fact. Completion is {@code
 * status == DONE}, recorded with {@code completedAt} and held together by a check constraint, so the
 * two cannot come apart. Two independent fields would have needed a rule for what a completed subtask
 * that is still in review means.
 *
 * <p>The status enum is the task's. The four columns on the board are the same four columns, and a
 * second enum with the same names would be a second thing to keep in step with the same check
 * constraint.
 *
 * <p>The project is carried beside the task so the assignee can be keyed to {@code project_members}
 * exactly as a task's is: somebody cannot be given a subtask on a project they are not on.
 */
@Entity
@Table(name = "subtasks")
class Subtask {

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

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Subtask() {
        // for JPA
    }

    static Subtask create(
            UUID workspaceId, UUID projectId, UUID taskId, String title, int position, UUID createdByUserId) {
        Subtask subtask = new Subtask();
        subtask.workspaceId = workspaceId;
        subtask.projectId = projectId;
        subtask.taskId = taskId;
        subtask.title = title;
        subtask.status = TaskStatus.TODO;
        subtask.position = position;
        subtask.createdByUserId = createdByUserId;
        return subtask;
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

    void retitle(String newTitle) {
        this.title = newTitle;
    }

    void assignTo(UUID userId) {
        this.assigneeUserId = userId;
    }

    void clearAssignee() {
        this.assigneeUserId = null;
    }

    void dueOn(LocalDate date) {
        this.dueDate = date;
    }

    void positionAt(int newPosition) {
        this.position = newPosition;
    }

    /** The one path that writes the status, so the completion timestamp cannot fall out of step. */
    void moveTo(TaskStatus target, Instant now) {
        this.status = target;
        this.completedAt = target.isComplete() ? now : null;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getProjectId() {
        return projectId;
    }

    UUID getTaskId() {
        return taskId;
    }

    String getTitle() {
        return title;
    }

    UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    TaskStatus getStatus() {
        return status;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    int getPosition() {
        return position;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
