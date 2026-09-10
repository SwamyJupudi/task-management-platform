package com.company.taskmanagementplatform.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.tasks.dto.TaskDependencyResponse;
import com.company.taskmanagementplatform.tasks.dto.TaskLinkResponse;

/**
 * One task waiting on another, and the four ways that can go wrong.
 *
 * <p>Self-dependency and a duplicate pair are refused by the database, and refused here first so the
 * caller reads a sentence rather than a constraint violation. A dependency across projects is
 * refused by the composite foreign keys and cannot be expressed at all. The fourth, a cycle, is the
 * only one a constraint cannot state.
 *
 * <p><strong>Cycles.</strong> Before inserting, one recursive query asks whether the task is already
 * reachable from its proposed blocker. If it is, the new edge would close a loop and the request is
 * refused. The walk runs in the database rather than in Java, so it costs one round trip however
 * deep the chain is.
 *
 * <p>The check and the insert are taken under an advisory lock on the project. Without it two
 * requests could each look at a graph that has no cycle, each insert an edge that does not create
 * one on its own, and together close a loop that neither of them saw. The lock is transaction
 * scoped, so it is released on commit or rollback with nothing to remember, and it is keyed on the
 * project because a dependency cannot leave one.
 */
@Service
public class TaskDependencyService {

    /**
     * A namespace for the two-integer form of the advisory lock, so a key derived from a project
     * cannot collide with one some future feature derives from something else.
     */
    private static final int PROJECT_DEPENDENCY_LOCK_NAMESPACE = 0x7A5C;

    private static final String LOCK_PROJECT = "SELECT pg_advisory_xact_lock(?, hashtext(?))";

    /**
     * Every task reachable by following blockers out from a starting point.
     *
     * <p>UNION rather than UNION ALL, which is what makes this terminate on a graph that already
     * contains a cycle: a node already seen is not queued again.
     */
    private static final String REACHES =
            """
            WITH RECURSIVE reachable(id) AS (
                SELECT depends_on_task_id FROM task_dependencies WHERE task_id = ?
                UNION
                SELECT d.depends_on_task_id
                FROM task_dependencies d
                JOIN reachable r ON d.task_id = r.id
            )
            SELECT EXISTS (SELECT 1 FROM reachable WHERE id = ?)
            """;

    private final TaskDependencyRepository dependencies;
    private final TaskRepository tasks;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    TaskDependencyService(
            TaskDependencyRepository dependencies,
            TaskRepository tasks,
            JdbcTemplate jdbc,
            ApplicationEventPublisher events) {
        this.dependencies = dependencies;
        this.tasks = tasks;
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public TaskDependencyResponse describe(UUID taskId) {
        return new TaskDependencyResponse(taskId, blockersOf(taskId), blockedBy(taskId));
    }

    /**
     * Records that {@code taskId} waits on {@code dependsOnTaskId}.
     *
     * @throws BadRequestException if the two are the same task, or the blocker is not in this project
     * @throws ConflictException if the pair already exists, or the edge would close a cycle
     */
    @Transactional
    public TaskDependencyResponse add(TaskRef task, UUID dependsOnTaskId, UUID actorUserId) {
        if (task.taskId().equals(dependsOnTaskId)) {
            throw new BadRequestException("A task cannot wait on itself.");
        }
        if (!tasks.existsByIdAndProjectIdAndDeletedAtIsNull(dependsOnTaskId, task.projectId())) {
            // Deliberately the same answer whether the task is in another project,
            // in another workspace, or was never real. A more precise message would
            // confirm the identifier names something.
            throw new BadRequestException("That task is not in this project.");
        }

        lockProject(task.projectId());

        if (dependencies.existsByTaskIdAndDependsOnTaskId(task.taskId(), dependsOnTaskId)) {
            throw new ConflictException("That task already waits on this one.");
        }
        if (reaches(dependsOnTaskId, task.taskId())) {
            throw new ConflictException(
                    "That would make the two tasks wait on each other, directly or through others.");
        }

        dependencies.save(TaskDependency.of(
                task.workspaceId(), task.projectId(), task.taskId(), dependsOnTaskId, actorUserId));
        dependencies.flush();

        events.publishEvent(new TaskEvents.TaskDependencyAdded(
                task.workspaceId(), task.projectId(), task.taskId(), dependsOnTaskId, actorUserId));

        return describe(task.taskId());
    }

    @Transactional
    public void remove(TaskRef task, UUID dependsOnTaskId, UUID actorUserId) {
        TaskDependency dependency = dependencies
                .findByTaskIdAndDependsOnTaskId(task.taskId(), dependsOnTaskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Task dependency", dependsOnTaskId));

        dependencies.delete(dependency);
        events.publishEvent(new TaskEvents.TaskDependencyRemoved(
                task.workspaceId(), task.projectId(), task.taskId(), dependsOnTaskId, actorUserId));
    }

    /**
     * Serialises dependency changes within one project.
     *
     * <p>Transaction scoped, so nothing has to release it, and taken before the cycle check rather
     * than after, which is the whole point: the graph must not change between being examined and
     * being added to.
     */
    private void lockProject(UUID projectId) {
        // The function returns void, so the result set is read and discarded.
        jdbc.query(LOCK_PROJECT, rs -> null, PROJECT_DEPENDENCY_LOCK_NAMESPACE, projectId.toString());
    }

    /** Whether {@code target} can be reached by following blockers out from {@code from}. */
    private boolean reaches(UUID from, UUID target) {
        return Boolean.TRUE.equals(jdbc.queryForObject(REACHES, Boolean.class, from, target));
    }

    private List<TaskLinkResponse> blockersOf(UUID taskId) {
        return links(tasks.findBlockersOf(List.of(taskId)));
    }

    private List<TaskLinkResponse> blockedBy(UUID taskId) {
        return links(tasks.findBlockedBy(List.of(taskId)));
    }

    /**
     * The key on each link is the number alone.
     *
     * <p>This endpoint answers about one task, whose project the caller already knows, so resolving
     * the project key again per row would be a query for something already on the screen. The task
     * listing composes the full form, where the project can differ from row to row.
     */
    private static List<TaskLinkResponse> links(List<Object[]> rows) {
        List<TaskLinkResponse> links = new ArrayList<>();
        for (Object[] row : rows) {
            int number = ((Number) row[2]).intValue();
            links.add(new TaskLinkResponse(
                    (UUID) row[1], number, String.valueOf(number), (String) row[3], ((TaskStatus) row[4]).name()));
        }
        return links;
    }
}
