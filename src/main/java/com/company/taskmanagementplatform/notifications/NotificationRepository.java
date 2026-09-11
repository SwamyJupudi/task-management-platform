package com.company.taskmanagementplatform.notifications;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private, like every repository in the platform. No module outside reaches this table. */
interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * The one way a row is written, and it is native for one reason: {@code ON CONFLICT DO NOTHING}.
     *
     * <p>That clause is what makes the deadline scan idempotent. A second run of the same day, an
     * overlapping run, or a retry after a half-finished batch all hit the partial unique index on
     * {@code dedupe_key} and write nothing, rather than the application having to ask first and race
     * anyway between the asking and the writing.
     *
     * <p>Every nullable parameter is cast explicitly. PostgreSQL cannot infer the type of a bound
     * null on its own, and the error it raises when it tries says nothing useful about which
     * parameter caused it.
     *
     * @return 1 when the row was written, 0 when an identical one already existed
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO notifications (
                        workspace_id, recipient_user_id, actor_user_id, type, entity_type,
                        entity_id, project_id, metadata, dedupe_key, created_at, updated_at)
                    VALUES (
                        CAST(:workspaceId AS uuid),
                        CAST(:recipientUserId AS uuid),
                        CAST(:actorUserId AS uuid),
                        :type,
                        :entityType,
                        CAST(:entityId AS uuid),
                        CAST(:projectId AS uuid),
                        CAST(:metadata AS jsonb),
                        CAST(:dedupeKey AS text),
                        CAST(:now AS timestamptz),
                        CAST(:now AS timestamptz))
                    ON CONFLICT DO NOTHING
                    """,
            nativeQuery = true)
    int insert(
            @Param("workspaceId") UUID workspaceId,
            @Param("recipientUserId") UUID recipientUserId,
            @Param("actorUserId") UUID actorUserId,
            @Param("type") String type,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("projectId") UUID projectId,
            @Param("metadata") String metadata,
            @Param("dedupeKey") String dedupeKey,
            @Param("now") Instant now);

    Page<Notification> findAllByRecipientUserIdAndWorkspaceIdOrderByCreatedAtDesc(
            UUID recipientUserId, UUID workspaceId, Pageable pageable);

    Page<Notification> findAllByRecipientUserIdAndWorkspaceIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID recipientUserId, UUID workspaceId, Pageable pageable);

    long countByRecipientUserIdAndWorkspaceIdAndReadAtIsNull(UUID recipientUserId, UUID workspaceId);

    /**
     * One notification, always narrowed to its recipient.
     *
     * <p>There is no lookup here that takes an identifier alone, for the reason the tasks repository
     * gives: somebody else's notification has to be indistinguishable from one that never existed,
     * and the surest way to guarantee that is to make the narrower question the only one that can be
     * asked.
     */
    Optional<Notification> findByIdAndRecipientUserIdAndWorkspaceId(
            UUID id, UUID recipientUserId, UUID workspaceId);

    /** Marking a whole feed read, in one statement rather than one per row. */
    @Modifying
    @Query(
            """
            UPDATE Notification n SET n.readAt = :now, n.updatedAt = :now
            WHERE n.recipientUserId = :recipientUserId
              AND n.workspaceId = :workspaceId
              AND n.readAt IS NULL
            """)
    int markAllRead(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("workspaceId") UUID workspaceId,
            @Param("now") Instant now);

    /** Somebody left a workspace: everything they were told about it goes with them. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.workspaceId = :workspaceId AND n.recipientUserId = :userId")
    int deleteForMemberOfWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    /** Somebody left one project: only that project's notifications go. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.projectId = :projectId AND n.recipientUserId = :userId")
    int deleteForMemberOfProject(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    /** The account itself was removed. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipientUserId = :userId")
    int deleteForUser(@Param("userId") UUID userId);
}
