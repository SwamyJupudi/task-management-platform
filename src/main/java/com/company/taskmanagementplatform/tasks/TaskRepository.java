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

    // -----------------------------------------------------------------------
    // Analytics, for the reports module through TaskAnalyticsFacade.
    //
    // Every one of these aggregates in SQL and returns at most a handful of
    // rows. None of them loads tasks so that somebody else can count them,
    // which is what the requirements mean by not fetching records
    // unnecessarily.
    //
    // The scope predicate is spelled "unrestricted, or one of these projects"
    // and is joined to the filters with AND, so no filter can widen it. When
    // the caller holds project:read_any the identifier list is never consulted,
    // and the facade passes a single impossible identifier rather than an empty
    // list, because IN () is not valid SQL.
    //
    // A null bound is expressed as a boolean flag beside a non-null sentinel
    // rather than as ":param IS NULL". PostgreSQL has to know the type of every
    // placeholder at parse time, and a lone "$n IS NULL" over a timestamp gives
    // it nothing to infer from: it refuses the statement with "could not
    // determine data type of parameter". A flag carries its own type, and it is
    // the shape the trend queries below already use.
    // -----------------------------------------------------------------------

    /**
     * How many live tasks hold each status, inside the caller's reach.
     *
     * @return rows of {@code [status, count]}
     */
    @Query(
            """
            SELECT t.status, count(t) FROM Task t
            WHERE t.workspaceId = :workspaceId
              AND t.deletedAt IS NULL
              AND (:unrestricted = true OR t.projectId IN :projectIds)
              AND (:allAssignees = true OR t.assigneeUserId = :assigneeUserId)
              AND (:wholeHistory = true
                   OR (t.createdAt >= :createdFrom AND t.createdAt < :createdTo))
            GROUP BY t.status
            """)
    List<Object[]> countByStatusInScope(
            @Param("workspaceId") UUID workspaceId,
            @Param("unrestricted") boolean unrestricted,
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("allAssignees") boolean allAssignees,
            @Param("assigneeUserId") UUID assigneeUserId,
            @Param("wholeHistory") boolean wholeHistory,
            @Param("createdFrom") java.time.Instant createdFrom,
            @Param("createdTo") java.time.Instant createdTo);

    /**
     * The same, by priority.
     *
     * @return rows of {@code [priority, count]}
     */
    @Query(
            """
            SELECT t.priority, count(t) FROM Task t
            WHERE t.workspaceId = :workspaceId
              AND t.deletedAt IS NULL
              AND (:unrestricted = true OR t.projectId IN :projectIds)
              AND (:allAssignees = true OR t.assigneeUserId = :assigneeUserId)
              AND (:wholeHistory = true
                   OR (t.createdAt >= :createdFrom AND t.createdAt < :createdTo))
            GROUP BY t.priority
            """)
    List<Object[]> countByPriorityInScope(
            @Param("workspaceId") UUID workspaceId,
            @Param("unrestricted") boolean unrestricted,
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("allAssignees") boolean allAssignees,
            @Param("assigneeUserId") UUID assigneeUserId,
            @Param("wholeHistory") boolean wholeHistory,
            @Param("createdFrom") java.time.Instant createdFrom,
            @Param("createdTo") java.time.Instant createdTo);

    /**
     * Total, open, overdue and done in one pass.
     *
     * <p>One query rather than four, because they differ only in a predicate and four round trips to
     * count the same rows would be three too many.
     *
     * @return one row of {@code [total, open, overdue, done]}
     */
    @Query(
            """
            SELECT count(t),
                   sum(CASE WHEN t.status <> :done THEN 1 ELSE 0 END),
                   sum(CASE WHEN t.status <> :done AND t.dueDate IS NOT NULL AND t.dueDate < :today
                            THEN 1 ELSE 0 END),
                   sum(CASE WHEN t.status = :done THEN 1 ELSE 0 END)
            FROM Task t
            WHERE t.workspaceId = :workspaceId
              AND t.deletedAt IS NULL
              AND (:unrestricted = true OR t.projectId IN :projectIds)
              AND (:assigneeUserId IS NULL OR t.assigneeUserId = :assigneeUserId)
            """)
    List<Object[]> countersInScope(
            @Param("workspaceId") UUID workspaceId,
            @Param("unrestricted") boolean unrestricted,
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("assigneeUserId") UUID assigneeUserId,
            @Param("done") TaskStatus done,
            @Param("today") java.time.LocalDate today);

    /**
     * Per-project counts for a named set of projects, in one query.
     *
     * <p>The set is already inside the caller's reach when it gets here: the projects report pages
     * projects first and asks about that page, so there is no scope predicate to repeat.
     *
     * @return rows of {@code [projectId, total, done, overdue]}
     */
    @Query(
            """
            SELECT t.projectId, count(t),
                   sum(CASE WHEN t.status = :done THEN 1 ELSE 0 END),
                   sum(CASE WHEN t.status <> :done AND t.dueDate IS NOT NULL AND t.dueDate < :today
                            THEN 1 ELSE 0 END)
            FROM Task t
            WHERE t.deletedAt IS NULL AND t.projectId IN :projectIds
            GROUP BY t.projectId
            """)
    List<Object[]> countByProject(
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("done") TaskStatus done,
            @Param("today") java.time.LocalDate today);

    /**
     * One row per person holding work, for the workload report.
     *
     * <p>The cardinality of this result is the number of people with a task in scope, so it is
     * bounded by the workspace roster rather than by the number of tasks. That is what makes it safe
     * to sort and page it after the query: the alternative, an ORDER BY over an aggregate alias
     * chosen by a request parameter, cannot be expressed without building SQL from a string.
     *
     * <p>Unassigned work is excluded rather than gathered under a null key. Workload is a statement
     * about people, and a row naming nobody is not one.
     *
     * @return rows of {@code [userId, open, inProgress, overdue, completedInPeriod, estimatedMinutes,
     *     actualMinutes]}
     */
    @Query(
            """
            SELECT t.assigneeUserId,
                   sum(CASE WHEN t.status <> :done THEN 1 ELSE 0 END),
                   sum(CASE WHEN t.status = :inProgress THEN 1 ELSE 0 END),
                   sum(CASE WHEN t.status <> :done AND t.dueDate IS NOT NULL AND t.dueDate < :today
                            THEN 1 ELSE 0 END),
                   sum(CASE WHEN t.status = :done AND t.completedAt >= :from AND t.completedAt < :to
                            THEN 1 ELSE 0 END),
                   sum(CASE WHEN t.status <> :done THEN coalesce(t.estimatedMinutes, 0) ELSE 0 END),
                   sum(CASE WHEN t.status <> :done THEN coalesce(t.actualMinutes, 0) ELSE 0 END)
            FROM Task t
            WHERE t.workspaceId = :workspaceId
              AND t.deletedAt IS NULL
              AND t.assigneeUserId IS NOT NULL
              AND (:unrestricted = true OR t.projectId IN :projectIds)
            GROUP BY t.assigneeUserId
            """)
    List<Object[]> workloadInScope(
            @Param("workspaceId") UUID workspaceId,
            @Param("unrestricted") boolean unrestricted,
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("done") TaskStatus done,
            @Param("inProgress") TaskStatus inProgress,
            @Param("today") java.time.LocalDate today,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);

    /**
     * Tasks completed per bucket, for the productivity trend.
     *
     * <p>Native, and the only query in this module that has to be. Bucketing by day, week or month
     * in the workspace's own timezone is {@code date_trunc} over {@code AT TIME ZONE}, and JPQL can
     * express neither. The granularity is bound rather than concatenated, and its value comes from an
     * enum, so a request parameter cannot reach the SQL.
     *
     * <p>The window is compared against the raw {@code completed_at} column rather than against the
     * bucketed expression, so the partial index on it is usable. The bounds are computed from the
     * local dates in the reports module, which is where the timezone is resolved.
     *
     * <p><strong>The source is {@code completed_at}, with a known caveat.</strong> Re-opening a
     * finished task clears the column, so that task leaves the bucket it was once in. The true
     * history is in {@code activity_logs}, which is append-only, but reaching completions there means
     * an unindexed {@code jsonb} filter that {@code database.md} defers with the rest of the query
     * tuning. This is recorded rather than discovered.
     *
     * @return rows of {@code [bucketStart, count]}, ordered, with empty buckets absent
     */
    @Query(
            nativeQuery = true,
            value =
                    """
                    SELECT CAST(date_trunc(CAST(:granularity AS text),
                                           t.completed_at AT TIME ZONE CAST(:zone AS text)) AS date),
                           count(*)
                    FROM tasks t
                    WHERE t.workspace_id = :workspaceId
                      AND t.deleted_at IS NULL
                      AND t.completed_at IS NOT NULL
                      AND t.completed_at >= :from
                      AND t.completed_at < :to
                      AND (:unrestricted = TRUE OR t.project_id IN (:projectIds))
                      AND (:allAssignees = TRUE OR t.assignee_user_id = :assigneeUserId)
                    GROUP BY 1
                    ORDER BY 1
                    """)
    List<Object[]> completionTrend(
            @Param("workspaceId") UUID workspaceId,
            @Param("unrestricted") boolean unrestricted,
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("allAssignees") boolean allAssignees,
            @Param("assigneeUserId") UUID assigneeUserId,
            @Param("granularity") String granularity,
            @Param("zone") String zone,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);

    /**
     * Tasks created per bucket, which is the other half of the same chart.
     *
     * <p>A completion line on its own says nothing about whether a team is keeping up. The created
     * line beside it is what turns two numbers into a trend.
     *
     * @return rows of {@code [bucketStart, count]}
     */
    @Query(
            nativeQuery = true,
            value =
                    """
                    SELECT CAST(date_trunc(CAST(:granularity AS text),
                                           t.created_at AT TIME ZONE CAST(:zone AS text)) AS date),
                           count(*)
                    FROM tasks t
                    WHERE t.workspace_id = :workspaceId
                      AND t.deleted_at IS NULL
                      AND t.created_at >= :from
                      AND t.created_at < :to
                      AND (:unrestricted = TRUE OR t.project_id IN (:projectIds))
                      AND (:allAssignees = TRUE OR t.assignee_user_id = :assigneeUserId)
                    GROUP BY 1
                    ORDER BY 1
                    """)
    List<Object[]> creationTrend(
            @Param("workspaceId") UUID workspaceId,
            @Param("unrestricted") boolean unrestricted,
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("allAssignees") boolean allAssignees,
            @Param("assigneeUserId") UUID assigneeUserId,
            @Param("granularity") String granularity,
            @Param("zone") String zone,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);

    /**
     * The overdue listing, paged and sorted by the caller within an allowlist.
     *
     * <p>"Overdue" is open work with a due date already past. Finished work is never overdue however
     * late it was, which is the same rule the {@code overdue} filter on the task listing applies;
     * the two are held together by a test rather than by hope.
     *
     * @return rows of {@code [id, projectId, taskNumber, title, status, priority, assigneeUserId,
     *     dueDate]}
     */
    @Query(
            value =
                    """
                    SELECT t.id, t.projectId, t.taskNumber, t.title, t.status, t.priority,
                           t.assigneeUserId, t.dueDate
                    FROM Task t
                    WHERE t.workspaceId = :workspaceId
                      AND t.deletedAt IS NULL
                      AND t.status <> :done
                      AND t.dueDate IS NOT NULL
                      AND t.dueDate < :today
                      AND (:unrestricted = true OR t.projectId IN :projectIds)
                      AND (:assigneeUserId IS NULL OR t.assigneeUserId = :assigneeUserId)
                    """,
            countQuery =
                    """
                    SELECT count(t) FROM Task t
                    WHERE t.workspaceId = :workspaceId
                      AND t.deletedAt IS NULL
                      AND t.status <> :done
                      AND t.dueDate IS NOT NULL
                      AND t.dueDate < :today
                      AND (:unrestricted = true OR t.projectId IN :projectIds)
                      AND (:assigneeUserId IS NULL OR t.assigneeUserId = :assigneeUserId)
                    """)
    org.springframework.data.domain.Page<Object[]> findOverdue(
            @Param("workspaceId") UUID workspaceId,
            @Param("unrestricted") boolean unrestricted,
            @Param("projectIds") java.util.Collection<UUID> projectIds,
            @Param("assigneeUserId") UUID assigneeUserId,
            @Param("done") TaskStatus done,
            @Param("today") java.time.LocalDate today,
            org.springframework.data.domain.Pageable pageable);

    /**
     * One person's next deadlines, soonest first.
     *
     * <p>Narrowed to the assignee rather than to a scope, because the assignee is a foreign key into
     * {@code project_members}: a task cannot be assigned to somebody outside the project that holds
     * it, so "assigned to me" is a subset of "visible to me" by construction rather than by a check.
     * That is the same property that makes My Tasks safe.
     *
     * @return rows in the shape {@link #findOverdue} returns
     */
    @Query(
            """
            SELECT t.id, t.projectId, t.taskNumber, t.title, t.status, t.priority,
                   t.assigneeUserId, t.dueDate
            FROM Task t
            WHERE t.workspaceId = :workspaceId
              AND t.deletedAt IS NULL
              AND t.assigneeUserId = :assigneeUserId
              AND t.status <> :done
              AND t.dueDate BETWEEN :from AND :to
            ORDER BY t.dueDate, t.taskNumber
            """)
    List<Object[]> findUpcomingFor(
            @Param("workspaceId") UUID workspaceId,
            @Param("assigneeUserId") UUID assigneeUserId,
            @Param("done") TaskStatus done,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to,
            org.springframework.data.domain.Pageable pageable);
}
