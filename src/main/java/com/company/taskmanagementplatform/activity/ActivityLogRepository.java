package com.company.taskmanagementplatform.activity;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Package-private, like every repository in the platform.
 *
 * <p>Only reads and inserts. {@code JpaRepository} brings {@code delete} and {@code save} with it and
 * neither is called on an existing row; the trigger in {@code V7} is what makes that a guarantee
 * rather than an intention.
 */
interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    Page<ActivityLog> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<ActivityLog> findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID projectId, Pageable pageable);

    /**
     * One person's own actions in one workspace, newest first.
     *
     * <p>Added for the employee dashboard's "recent activity", which is a record of what this person
     * did rather than of what happened around them. It is not a widening of the audit read: every row
     * it returns names the caller as the actor, so it discloses nothing they did not do themselves.
     * Browsing the workspace remains {@code activity:read}.
     */
    Page<ActivityLog> findAllByWorkspaceIdAndActorUserIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID actorUserId, Pageable pageable);

    /**
     * Everything that happened to one task, including what happened to the things hanging off it.
     *
     * <p>A comment, a subtask and an attachment each record themselves as their own entity, because
     * that is what they are, so matching on the entity alone would give a task history with no
     * comments in it and the requirements print "Anil added a comment" as an example of exactly what
     * a task history should show. The second half of the predicate finds those rows through the task
     * identifier their metadata carries.
     *
     * <p>The JSON lookup is not indexed, and deliberately: it runs inside {@code project_id}, which is
     * indexed, so it reads one project's rows rather than the workspace's. An expression index on
     * {@code metadata->>'taskId'} belongs with the other query tuning in the phase that has the data
     * volume to justify it.
     */
    @Query(
            value =
                    """
                    SELECT * FROM activity_logs
                    WHERE workspace_id = :workspaceId
                      AND project_id = :projectId
                      AND (entity_id = :taskId OR metadata ->> 'taskId' = CAST(:taskId AS text))
                    ORDER BY created_at DESC
                    """,
            countQuery =
                    """
                    SELECT count(*) FROM activity_logs
                    WHERE workspace_id = :workspaceId
                      AND project_id = :projectId
                      AND (entity_id = :taskId OR metadata ->> 'taskId' = CAST(:taskId AS text))
                    """,
            nativeQuery = true)
    Page<ActivityLog> findTaskHistory(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectId") UUID projectId,
            @Param("taskId") UUID taskId,
            Pageable pageable);
}
