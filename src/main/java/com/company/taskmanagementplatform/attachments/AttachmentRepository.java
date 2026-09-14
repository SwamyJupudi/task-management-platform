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

    /**
     * A bounded page of live attachments no scanner has cleared, oldest first.
     *
     * <p>What the rescan reads. Oldest first so a backlog is worked through in upload order, and because a
     * file somebody added years ago is the one most likely to predate scanning entirely.
     *
     * <p>Soft-deleted rows are excluded. Nobody can download them, so scanning them would spend a scanner
     * call and a read from the object store to change a column on a row that is waiting to be purged.
     *
     * <p>The predicate is {@code scan_status <> 'CLEAN'} rather than {@code = 'PENDING'}, which means a
     * {@code REJECTED} row would be re-read on the next run. That is deliberate: the alternative is a
     * predicate that has to be extended every time a state is added, and this one fails towards re-checking a
     * file rather than towards silently skipping one. The service filters the rejections out.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT a FROM Attachment a WHERE a.deletedAt IS NULL AND a.scanStatus <> 'CLEAN' "
                    + "ORDER BY a.createdAt ASC")
    List<Attachment> findUnscanned(org.springframework.data.domain.Pageable pageable);

    /**
     * A bounded page of attachments deleted long enough ago to be past their retention, oldest first.
     *
     * <p>For the byte purge. Oldest first so that a backlog is worked through in the order things were
     * deleted, and so that a run stopping early leaves the newest — most likely to be restored —
     * untouched for longest.
     *
     * <p>The whole entity rather than a projection of the two columns the purge needs, because the purge
     * deletes the row it has just reclaimed and Spring Data deletes entities, not projections. A page of
     * a couple of hundred is not the query worth optimising here; the object-store call per row is.
     *
     * @param deletedBefore attachments whose {@code deleted_at} is strictly before this are eligible
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT a FROM Attachment a WHERE a.deletedAt IS NOT NULL AND a.deletedAt < :deletedBefore "
                    + "ORDER BY a.deletedAt ASC")
    List<Attachment> findPurgeable(
            @org.springframework.data.repository.query.Param("deletedBefore") java.time.Instant deletedBefore,
            org.springframework.data.domain.Pageable pageable);
}
