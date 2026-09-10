package com.company.taskmanagementplatform.tasks;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    Optional<TaskDependency> findByTaskIdAndDependsOnTaskId(UUID taskId, UUID dependsOnTaskId);

    boolean existsByTaskIdAndDependsOnTaskId(UUID taskId, UUID dependsOnTaskId);

    /**
     * Removes every edge touching a task, in either direction.
     *
     * <p>Called when a task is soft deleted. The foreign keys cascade on a hard delete, which a soft
     * delete is not, so without this a deleted task would keep blocking live ones and the cycle walk
     * would traverse a row nothing can see.
     */
    long deleteAllByTaskIdOrDependsOnTaskId(UUID taskId, UUID sameTaskId);
}
