package com.company.taskmanagementplatform.tasks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private, like every repository in the platform. No module outside reaches this table. */
interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    /**
     * Always by workspace as well as by identifier.
     *
     * <p>There is no lookup here that takes a task identifier alone. A task from another workspace
     * has to be indistinguishable from one that never existed, and the surest way to guarantee that
     * is to make the narrower question the only one the repository can answer.
     */
    Optional<Task> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    /** Every live task of a project, for the cleanup that runs when the project is removed. */
    List<Task> findAllByProjectIdAndDeletedAtIsNull(UUID projectId);

    /** Live tasks assigned to somebody on one project, for the cleanup when they leave it. */
    List<Task> findAllByProjectIdAndAssigneeUserIdAndDeletedAtIsNull(UUID projectId, UUID assigneeUserId);

    /** Live tasks assigned to somebody anywhere in a workspace, for the cleanup when they leave it. */
    List<Task> findAllByWorkspaceIdAndAssigneeUserIdAndDeletedAtIsNull(UUID workspaceId, UUID assigneeUserId);

    /** The same, everywhere at once, for when the account itself is removed. */
    List<Task> findAllByAssigneeUserIdAndDeletedAtIsNull(UUID assigneeUserId);

    /**
     * Tasks naming somebody as reporter, which is a foreign key into {@code workspace_members}.
     *
     * <p>Not filtered on {@code deleted_at}: the constraint holds for a soft-deleted row too, so a
     * removal would still be refused if this only cleared the live ones.
     */
    List<Task> findAllByWorkspaceIdAndReporterUserId(UUID workspaceId, UUID reporterUserId);

    List<Task> findAllByReporterUserId(UUID reporterUserId);

    /** Whether a task belongs to this project, for the same-project rule on a dependency. */
    boolean existsByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

    /**
     * The tasks blocking a page of tasks, and the tasks they block, in one query each.
     *
     * @return rows of {@code [taskId, blockerId, blockerNumber, blockerTitle, blockerStatus]}
     */
    @Query(
            """
            SELECT d.taskId, t.id, t.taskNumber, t.title, t.status
            FROM TaskDependency d
            JOIN Task t ON t.id = d.dependsOnTaskId
            WHERE d.taskId IN :taskIds AND t.deletedAt IS NULL
            ORDER BY t.taskNumber
            """)
    List<Object[]> findBlockersOf(@Param("taskIds") java.util.Collection<UUID> taskIds);

    @Query(
            """
            SELECT d.dependsOnTaskId, t.id, t.taskNumber, t.title, t.status
            FROM TaskDependency d
            JOIN Task t ON t.id = d.taskId
            WHERE d.dependsOnTaskId IN :taskIds AND t.deletedAt IS NULL
            ORDER BY t.taskNumber
            """)
    List<Object[]> findBlockedBy(@Param("taskIds") java.util.Collection<UUID> taskIds);

    /**
     * The digest of a set of tasks, for the {@code notifications} module's page rendering.
     *
     * @return rows of {@code [id, workspaceId, projectId, taskNumber, title, assigneeUserId,
     *     reporterUserId, dueDate]}
     */
    @Query(
            """
            SELECT t.id, t.workspaceId, t.projectId, t.taskNumber, t.title,
                   t.assigneeUserId, t.reporterUserId, t.dueDate
            FROM Task t
            WHERE t.id IN :taskIds AND t.deletedAt IS NULL
            """)
    List<Object[]> findDigests(@Param("taskIds") java.util.Collection<UUID> taskIds);

    /**
     * Live, unfinished, assigned tasks whose due date falls in a window, oldest deadline first.
     *
     * <p>The deadline scan's one query, and the only query in the platform whose size grows with the
     * whole estate rather than with one workspace. It is paged for exactly that reason, and ordered
     * by identifier as well as by date so that two tasks due the same day cannot swap places between
     * one page and the next and leave one of them unread.
     *
     * <p>Unassigned tasks are excluded here rather than filtered afterwards: there is nobody to tell,
     * and reading them only to discard them is the kind of waste the requirements name.
     *
     * @return rows in the shape {@link #findDigests} returns
     */
    @Query(
            """
            SELECT t.id, t.workspaceId, t.projectId, t.taskNumber, t.title,
                   t.assigneeUserId, t.reporterUserId, t.dueDate
            FROM Task t
            WHERE t.dueDate BETWEEN :from AND :to
              AND t.status <> :done
              AND t.assigneeUserId IS NOT NULL
              AND t.deletedAt IS NULL
            ORDER BY t.dueDate, t.id
            """)
    org.springframework.data.domain.Page<Object[]> findDueDigests(
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to,
            @Param("done") TaskStatus done,
            org.springframework.data.domain.Pageable pageable);
}
