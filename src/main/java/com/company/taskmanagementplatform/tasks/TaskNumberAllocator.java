package com.company.taskmanagementplatform.tasks;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out the next task number for a project, safely when several people create at once.
 *
 * <p>One statement does it. The insert either creates the counter row at one, or conflicts with the
 * row already there and increments it, and either way it returns the number it settled on.
 * Concurrent creators collide on the primary key, block on the row until the holder commits, and
 * each leaves with a number nobody else has. There is no read followed by a write for two
 * transactions to interleave inside.
 *
 * <p>That is the whole reason this is not {@code SELECT max(task_number) + 1}. Two requests running
 * that at the same moment both read the same maximum, both add one, and one of them then violates
 * the unique key, if it is lucky enough for the unique key to exist. The counter turns a race into a
 * queue.
 *
 * <p>{@code MANDATORY} because a number handed out and then not used is a gap in the project's
 * numbering. It has to be allocated inside the transaction that inserts the task, so that a rolled
 * back creation rolls the counter back with it.
 *
 * <p>Written against {@code JdbcTemplate} rather than the entity manager. The statement is an upsert
 * with a RETURNING clause and no entity behind it, and it shares the transaction's connection, so
 * the row lock it takes is the one the surrounding transaction holds.
 */
@Component
class TaskNumberAllocator {

    private static final String ALLOCATE =
            """
            INSERT INTO project_task_counters (project_id, workspace_id, next_number)
            VALUES (?, ?, 1)
            ON CONFLICT (project_id)
            DO UPDATE SET next_number = project_task_counters.next_number + 1,
                          updated_at = now()
            RETURNING next_number
            """;

    private final JdbcTemplate jdbc;

    TaskNumberAllocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    int nextFor(UUID projectId, UUID workspaceId) {
        Integer allocated = jdbc.queryForObject(ALLOCATE, Integer.class, projectId, workspaceId);
        if (allocated == null) {
            // The statement always returns a row; a null here would mean the counter
            // row vanished mid-statement, which the foreign key makes impossible.
            throw new IllegalStateException("No task number was allocated for project " + projectId);
        }
        return allocated;
    }
}
