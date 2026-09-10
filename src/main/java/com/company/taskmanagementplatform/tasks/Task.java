package com.company.taskmanagementplatform.tasks;

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

/**
 * One piece of work inside one project.
 *
 * <p>The project, the assignee and the reporter are plain identifiers rather than associations,
 * matching the convention across the other modules: every query here already knows the identifier it
 * wants. Each is pinned by a composite foreign key rather than by a service check, so an assignee
 * who is not on the project and a reporter who is not in the workspace are writes the database
 * refuses.
 *
 * <p>The number is allocated per project and never reused, so {@code PROJ-12} means one task for as
 * long as the project exists. The rendered key is composed from the project's key when the task is
 * mapped to a response; storing a copy of it here would be a second thing to keep in step for no
 * gain.
 *
 * <p>{@code completedAt} is set and cleared only through {@link #moveTo}, so it cannot drift from
 * the status. The database holds the same rule with a check constraint, which is what catches any
 * future path that forgets.
 */
@Entity
@Table(name = "tasks")
class Task {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "task_number", nullable = false, updatable = false)
    private int taskNumber;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "reporter_user_id")
    private UUID reporterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TaskPriority priority;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "board_position", nullable = false)
    private int boardPosition;

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

    protected Task() {
        // for JPA
    }

    static Task create(UUID workspaceId, UUID projectId, int taskNumber, String title, UUID createdByUserId) {
        Task task = new Task();
        task.workspaceId = workspaceId;
        task.projectId = projectId;
        task.taskNumber = taskNumber;
        task.title = title;
        task.status = TaskStatus.TODO;
        task.priority = TaskPriority.MEDIUM;
        task.boardPosition = 0;
        task.createdByUserId = createdByUserId;
        return task;
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

    void describe(String newDescription) {
        this.description = newDescription;
    }

    void assignTo(UUID userId) {
        this.assigneeUserId = userId;
    }

    void clearAssignee() {
        this.assigneeUserId = null;
    }

    void reportedBy(UUID userId) {
        this.reporterUserId = userId;
    }

    void clearReporter() {
        this.reporterUserId = null;
    }

    void reprioritise(TaskPriority newPriority) {
        this.priority = newPriority;
    }

    void schedule(LocalDate newStart, LocalDate newDue) {
        this.startDate = newStart;
        this.dueDate = newDue;
    }

    void estimate(Integer minutes) {
        this.estimatedMinutes = minutes;
    }

    void record(Integer minutes) {
        this.actualMinutes = minutes;
    }

    void positionAt(int position) {
        this.boardPosition = position;
    }

    /** The one path that writes the status, so the completion timestamp cannot fall out of step. */
    void moveTo(TaskStatus target, Instant now) {
        this.status = target;
        this.completedAt = target.isComplete() ? now : null;
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
    }

    boolean isAssignedTo(UUID userId) {
        return assigneeUserId != null && assigneeUserId.equals(userId);
    }

    boolean isReportedBy(UUID userId) {
        return reporterUserId != null && reporterUserId.equals(userId);
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

    int getTaskNumber() {
        return taskNumber;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    UUID getReporterUserId() {
        return reporterUserId;
    }

    TaskStatus getStatus() {
        return status;
    }

    TaskPriority getPriority() {
        return priority;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    Integer getActualMinutes() {
        return actualMinutes;
    }

    int getBoardPosition() {
        return boardPosition;
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
