package com.company.taskmanagementplatform.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.projects.ProjectScope;

/**
 * What the tasks module publishes to {@code reports}, and nothing more.
 *
 * <p>A third facade beside {@link TaskAccessGuard} and {@link TaskNotificationFacade}, and the
 * reason is the one phase seven already recorded when it split the second off the first. The guard
 * authorizes: every method on it asks who the caller is and may refuse. Nothing here authorizes
 * anything. It is handed a {@link ProjectScope} the guard has already resolved, and its whole job is
 * to apply that scope inside an aggregate query. Mixing the two on one class would be an invitation
 * to call an unauthorized method from a request path.
 *
 * <p><strong>Read-only, always.</strong> Nothing here writes, and every method answers with values
 * rather than entities: a facade runs in its own read-only transaction, so an entity handed out of
 * this module would arrive detached and every change made to it would be silently discarded.
 *
 * <p><strong>Every aggregate is computed in SQL.</strong> No method loads a collection of tasks so
 * that another module can count it. That is the requirements' own instruction about not fetching
 * records unnecessarily, and it is the reason this class exists at all rather than {@code reports}
 * simply listing tasks and adding them up.
 *
 * <p>The scope is applied inside each query, joined to the filters with AND, so no filter can widen
 * it. A report is therefore not a way past the scope layer, which is the property
 * {@code ReportVisibilityIT} exists to keep true.
 */
@Service
public class TaskAnalyticsFacade {

    /**
     * Stands in for the empty list when a caller reaches everything.
     *
     * <p>An {@code IN ()} with no members is not valid SQL, and the scope predicate is written as
     * "unrestricted, or one of these". When the first half is true the second is never consulted, so
     * a single identifier that can match nothing is the cheapest thing to put there. The task
     * listing already uses the same stand-in for a team with no projects.
     */
    private static final UUID NONE = new UUID(0L, 0L);

    /**
     * Stands in for an absent window bound, for the same reason {@link #NONE} stands in for an empty
     * list.
     *
     * <p>PostgreSQL has to know the type of every placeholder when it parses a statement, and a bare
     * {@code $n IS NULL} over a timestamp gives it nothing to infer from: it refuses the whole
     * statement with "could not determine data type of parameter". So an absent window is a boolean
     * flag beside a bound that is never read, rather than a null bound tested for nullness. These two
     * span every instant the column can hold, so a query that ignored the flag would still be
     * correct rather than empty.
     */
    private static final Instant DAWN = Instant.EPOCH;

    private static final Instant DUSK = Instant.parse("9999-12-31T00:00:00Z");

    private final TaskRepository tasks;

    TaskAnalyticsFacade(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /** How many live tasks hold each status, inside the caller's reach. */
    @Transactional(readOnly = true)
    public List<TaskStatusCount> countsByStatus(UUID workspaceId, ProjectScope scope, TaskAnalyticsFilter filter) {
        if (scope.reachesNothing()) {
            return List.of();
        }

        List<TaskStatusCount> counts = new ArrayList<>();
        for (Object[] row : tasks.countByStatusInScope(
                workspaceId,
                scope.unrestricted(),
                scopeIds(scope),
                filter.assigneeUserId() == null,
                filter.assigneeUserId() == null ? NONE : filter.assigneeUserId(),
                filter.createdFrom() == null || filter.createdTo() == null,
                filter.createdFrom() == null ? DAWN : filter.createdFrom(),
                filter.createdTo() == null ? DUSK : filter.createdTo())) {
            counts.add(new TaskStatusCount((TaskStatus) row[0], ((Number) row[1]).longValue()));
        }
        return List.copyOf(counts);
    }

    /** The same, by priority. */
    @Transactional(readOnly = true)
    public List<TaskPriorityCount> countsByPriority(
            UUID workspaceId, ProjectScope scope, TaskAnalyticsFilter filter) {

        if (scope.reachesNothing()) {
            return List.of();
        }

        List<TaskPriorityCount> counts = new ArrayList<>();
        for (Object[] row : tasks.countByPriorityInScope(
                workspaceId,
                scope.unrestricted(),
                scopeIds(scope),
                filter.assigneeUserId() == null,
                filter.assigneeUserId() == null ? NONE : filter.assigneeUserId(),
                filter.createdFrom() == null || filter.createdTo() == null,
                filter.createdFrom() == null ? DAWN : filter.createdFrom(),
                filter.createdTo() == null ? DUSK : filter.createdTo())) {
            counts.add(new TaskPriorityCount((TaskPriority) row[0], ((Number) row[1]).longValue()));
        }
        return List.copyOf(counts);
    }

    /**
     * Total, open, overdue and done for one scope, in one query.
     *
     * @param today the current date <em>in the workspace's timezone</em>, resolved by the caller.
     *     Passing it in rather than reading a clock here is what keeps one request's figures
     *     consistent with each other and keeps this class testable without a fixed clock
     */
    @Transactional(readOnly = true)
    public TaskCounters counters(UUID workspaceId, ProjectScope scope, UUID assigneeUserId, LocalDate today) {
        if (scope.reachesNothing()) {
            return TaskCounters.empty();
        }

        List<Object[]> found = tasks.countersInScope(
                workspaceId, scope.unrestricted(), scopeIds(scope), assigneeUserId, TaskStatus.DONE, today);
        if (found.isEmpty()) {
            return TaskCounters.empty();
        }

        Object[] row = found.get(0);
        return new TaskCounters(count(row[0]), count(row[1]), count(row[2]), count(row[3]));
    }

    /** Per-project counts for a page of projects that is already inside the caller's reach. */
    @Transactional(readOnly = true)
    public List<ProjectTaskCounts> countsByProject(Collection<UUID> projectIds, LocalDate today) {
        if (projectIds.isEmpty()) {
            return List.of();
        }

        List<ProjectTaskCounts> counts = new ArrayList<>();
        for (Object[] row : tasks.countByProject(projectIds, TaskStatus.DONE, today)) {
            counts.add(new ProjectTaskCounts(
                    (UUID) row[0], count(row[1]), count(row[2]), count(row[3])));
        }
        return List.copyOf(counts);
    }

    /**
     * One row per person holding work in the caller's reach.
     *
     * <p>Bounded by the roster rather than by the task count: the group-by returns one row per
     * person, so sorting and paging it afterwards reads people rather than tasks.
     */
    @Transactional(readOnly = true)
    public List<AssigneeTaskCounts> workload(
            UUID workspaceId, ProjectScope scope, LocalDate today, Instant from, Instant to) {

        if (scope.reachesNothing()) {
            return List.of();
        }

        List<AssigneeTaskCounts> workload = new ArrayList<>();
        for (Object[] row : tasks.workloadInScope(
                workspaceId,
                scope.unrestricted(),
                scopeIds(scope),
                TaskStatus.DONE,
                TaskStatus.IN_PROGRESS,
                today,
                from,
                to)) {
            workload.add(new AssigneeTaskCounts(
                    (UUID) row[0],
                    count(row[1]),
                    count(row[2]),
                    count(row[3]),
                    count(row[4]),
                    count(row[5]),
                    count(row[6])));
        }
        return List.copyOf(workload);
    }

    /**
     * Tasks completed per bucket, in the workspace's own timezone.
     *
     * @param granularity one of {@code day}, {@code week} or {@code month}. Bound rather than
     *     concatenated, and produced by an enum, so a request parameter never reaches the SQL
     */
    @Transactional(readOnly = true)
    public List<TaskTrendPoint> completionTrend(
            UUID workspaceId,
            ProjectScope scope,
            UUID assigneeUserId,
            String granularity,
            String zone,
            Instant from,
            Instant to) {

        if (scope.reachesNothing()) {
            return List.of();
        }

        return toTrend(tasks.completionTrend(
                workspaceId,
                scope.unrestricted(),
                scopeIds(scope),
                assigneeUserId == null,
                assigneeUserId == null ? NONE : assigneeUserId,
                granularity,
                zone,
                from,
                to));
    }

    /** Tasks created per bucket, which is the line the completed one is read against. */
    @Transactional(readOnly = true)
    public List<TaskTrendPoint> creationTrend(
            UUID workspaceId,
            ProjectScope scope,
            UUID assigneeUserId,
            String granularity,
            String zone,
            Instant from,
            Instant to) {

        if (scope.reachesNothing()) {
            return List.of();
        }

        return toTrend(tasks.creationTrend(
                workspaceId,
                scope.unrestricted(),
                scopeIds(scope),
                assigneeUserId == null,
                assigneeUserId == null ? NONE : assigneeUserId,
                granularity,
                zone,
                from,
                to));
    }

    /** A page of overdue work in the caller's reach, sorted within the report's allowlist. */
    @Transactional(readOnly = true)
    public Page<TaskReportRow> overdue(
            UUID workspaceId, ProjectScope scope, UUID assigneeUserId, LocalDate today, Pageable pageable) {

        if (scope.reachesNothing()) {
            return Page.empty(pageable);
        }

        return tasks.findOverdue(
                        workspaceId,
                        scope.unrestricted(),
                        scopeIds(scope),
                        assigneeUserId,
                        TaskStatus.DONE,
                        today,
                        pageable)
                .map(TaskAnalyticsFacade::toRow);
    }

    /** One person's next deadlines, soonest first, bounded by a lead window and a count. */
    @Transactional(readOnly = true)
    public List<TaskReportRow> upcomingFor(
            UUID workspaceId, UUID assigneeUserId, LocalDate from, LocalDate to, int limit) {

        return tasks
                .findUpcomingFor(
                        workspaceId,
                        assigneeUserId,
                        TaskStatus.DONE,
                        from,
                        to,
                        PageRequest.of(0, Math.max(1, limit), Sort.unsorted()))
                .stream()
                .map(TaskAnalyticsFacade::toRow)
                .toList();
    }

    private static List<TaskTrendPoint> toTrend(List<Object[]> rows) {
        List<TaskTrendPoint> points = new ArrayList<>();
        for (Object[] row : rows) {
            points.add(new TaskTrendPoint(toLocalDate(row[0]), count(row[1])));
        }
        return List.copyOf(points);
    }

    /**
     * The bucket column, whatever the driver chose to hand back for a {@code date}.
     *
     * <p>Hibernate maps a native {@code date} to {@link LocalDate} today and mapped it to {@link
     * java.sql.Date} before; accepting either costs one branch and removes a way for an upgrade to
     * break a chart.
     */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static TaskReportRow toRow(Object[] row) {
        return new TaskReportRow(
                (UUID) row[0],
                (UUID) row[1],
                ((Number) row[2]).intValue(),
                (String) row[3],
                (TaskStatus) row[4],
                (TaskPriority) row[5],
                (UUID) row[6],
                (LocalDate) row[7]);
    }

    /** A SQL {@code sum} over no rows is null rather than zero, and a count is never negative. */
    private static long count(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static List<UUID> scopeIds(ProjectScope scope) {
        return scope.unrestricted() ? List.of(NONE) : scope.projectIds();
    }
}
