package com.company.taskmanagementplatform.subtasks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every repository in the platform. No module outside reaches this table. */
interface SubtaskRepository extends JpaRepository<Subtask, UUID> {

    /**
     * Always by parent as well as by identifier.
     *
     * <p>A subtask is reached only through its task, and the task is what was authorized, so a
     * subtask identifier belonging to a different task has to be indistinguishable from one that was
     * never real.
     */
    Optional<Subtask> findByIdAndTaskIdAndDeletedAtIsNull(UUID id, UUID taskId);

    List<Subtask> findAllByTaskIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(UUID taskId);

    /** For the cascade when the parent task, or its whole project, goes away. */
    List<Subtask> findAllByTaskIdAndDeletedAtIsNull(UUID taskId);

    List<Subtask> findAllByProjectIdAndDeletedAtIsNull(UUID projectId);

    /** Live subtasks assigned to somebody on one project, for the cleanup when they leave it. */
    List<Subtask> findAllByProjectIdAndAssigneeUserIdAndDeletedAtIsNull(UUID projectId, UUID assigneeUserId);

    /**
     * Everything assigned to somebody in a workspace, live or not.
     *
     * <p>Not filtered on {@code deleted_at}: the foreign key into {@code project_members} holds for a
     * soft-deleted row too, so clearing only the live ones would leave the removal refused.
     */
    List<Subtask> findAllByWorkspaceIdAndAssigneeUserId(UUID workspaceId, UUID assigneeUserId);

    List<Subtask> findAllByAssigneeUserId(UUID assigneeUserId);

    int countByTaskIdAndDeletedAtIsNull(UUID taskId);
}
