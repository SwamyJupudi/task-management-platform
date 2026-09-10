package com.company.taskmanagementplatform.tasks;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TaskLabelRepository extends JpaRepository<TaskLabel, TaskLabel.TaskLabelId> {

    long deleteAllByIdTaskId(UUID taskId);

    /**
     * The labels of a whole page of tasks in one query.
     *
     * <p>A lookup per row is exactly the shape that turns a list endpoint into N+1 without anybody
     * noticing until it is in production.
     *
     * @return rows of {@code [taskId, labelName]}
     */
    @Query(
            """
            SELECT tl.id.taskId, l.name FROM TaskLabel tl
            JOIN Label l ON l.id = tl.id.labelId
            WHERE tl.id.taskId IN :taskIds
            ORDER BY l.name
            """)
    List<Object[]> findNamesByTaskIds(@Param("taskIds") Collection<UUID> taskIds);
}
