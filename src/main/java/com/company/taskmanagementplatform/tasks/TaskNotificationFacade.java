package com.company.taskmanagementplatform.tasks;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the tasks module publishes to {@code notifications}, and nothing more.
 *
 * <p>A second facade beside {@link TaskAccessGuard} rather than three more methods on it, because
 * the two answer different kinds of question. The guard authorizes: every method on it asks who the
 * caller is and may refuse. Nothing here authorizes anything, and two of the three methods run with
 * no caller at all, on the scheduler's thread. Mixing those on one class would be an invitation to
 * call an unauthorized method from a request.
 *
 * <p><strong>Read-only, always.</strong> Nothing here writes, and everything answers with values
 * rather than entities, for the reason recorded in {@code architecture.md}: an entity handed out of
 * this module would arrive detached and every change made to it would be silently discarded.
 */
@Service
public class TaskNotificationFacade {

    private final TaskRepository tasks;

    TaskNotificationFacade(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /** One live task of one workspace, or empty when it does not exist or has been removed. */
    @Transactional(readOnly = true)
    public Optional<TaskDigest> find(UUID workspaceId, UUID taskId) {
        return tasks.findDigests(List.of(taskId)).stream()
                .map(TaskNotificationFacade::toDigest)
                .filter(digest -> digest.workspaceId().equals(workspaceId))
                .findFirst();
    }

    /**
     * A page of tasks in one query, keyed by identifier.
     *
     * <p>A notification feed shows many rows about many tasks, and a lookup per row is exactly how a
     * list endpoint becomes N+1 without anybody noticing until production.
     */
    @Transactional(readOnly = true)
    public Map<UUID, TaskDigest> findAllByIds(Collection<UUID> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, TaskDigest> byId = new LinkedHashMap<>();
        for (Object[] row : tasks.findDigests(taskIds)) {
            TaskDigest digest = toDigest(row);
            byId.put(digest.taskId(), digest);
        }
        return byId;
    }

    /**
     * One page of tasks whose deadline falls between two dates, for the scan.
     *
     * <p>Crosses every workspace, which no other query in this module does, and is the reason this
     * method is not on the guard: there is no caller to authorize and nothing to narrow it to.
     */
    @Transactional(readOnly = true)
    public Page<TaskDigest> findDueBetween(LocalDate from, LocalDate to, Pageable pageable) {
        return tasks.findDueDigests(from, to, TaskStatus.DONE, pageable).map(TaskNotificationFacade::toDigest);
    }

    private static TaskDigest toDigest(Object[] row) {
        return new TaskDigest(
                (UUID) row[0],
                (UUID) row[1],
                (UUID) row[2],
                (Integer) row[3],
                (String) row[4],
                (UUID) row[5],
                (UUID) row[6],
                (LocalDate) row[7]);
    }
}
