package com.company.taskmanagementplatform.attachments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every repository in the platform. No module outside reaches this table. */
interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Always by workspace as well as by identifier, so a file from another workspace is
     * indistinguishable from one that was never real.
     */
    Optional<Attachment> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    List<Attachment> findAllByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID taskId);

    /** For adoption: the files a comment is claiming, fetched in one query rather than one each. */
    List<Attachment> findAllByIdInAndTaskIdAndDeletedAtIsNull(List<UUID> ids, UUID taskId);

    /** For the cascades, when a comment, a task or a whole project goes away. */
    List<Attachment> findAllByCommentIdAndDeletedAtIsNull(UUID commentId);

    List<Attachment> findAllByTaskIdAndDeletedAtIsNull(UUID taskId);

    List<Attachment> findAllByProjectIdAndDeletedAtIsNull(UUID projectId);

    /**
     * How many live files there are and how much they occupy, in one row.
     *
     * <p>{@code coalesce} on the sum, so an installation with no attachments reads zero rather than
     * null. A panel showing a blank where a total belongs is worse than one showing nothing stored.
     *
     * <p>Declared as a list of one rather than a bare {@code Object[]}: a single-result projection
     * of several scalars is the one shape Spring Data is ambiguous about, and a list leaves nothing
     * to interpret.
     *
     * @return one row of {@code [count, totalBytes]}
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT count(a), coalesce(sum(a.sizeBytes), 0) FROM Attachment a WHERE a.deletedAt IS NULL")
    List<Object[]> countAndTotalBytes();
}
