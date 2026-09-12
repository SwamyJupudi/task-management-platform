package com.company.taskmanagementplatform.teams;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private, like every repository in the platform. No module outside reaches this table. */
interface TeamRepository extends JpaRepository<Team, UUID> {

    /**
     * Always by workspace as well as by identifier.
     *
     * <p>There is no lookup here that takes a team identifier alone. A team from another workspace
     * has to be indistinguishable from one that does not exist, and the surest way to guarantee that
     * is to make the narrower question the only one the repository can answer.
     */
    Optional<Team> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    Page<Team> findAllByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);

    Page<Team> findAllByWorkspaceIdAndStatusAndDeletedAtIsNull(
            UUID workspaceId, TeamStatus status, Pageable pageable);

    /** How many live teams a workspace has, for the administrator's dashboard. */
    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    /**
     * A named set of this workspace's live teams, for a report page that spans several.
     *
     * <p>By workspace as well as by identifier, like every other lookup here, so a team from
     * elsewhere is simply absent rather than reported.
     */
    List<Team> findAllByWorkspaceIdAndIdInAndDeletedAtIsNull(UUID workspaceId, Collection<UUID> ids);

    /** Every team this person leads in one workspace, deleted ones included, for the cleanup path. */
    List<Team> findAllByWorkspaceIdAndLeadUserId(UUID workspaceId, UUID leadUserId);

    /** Every team this person leads anywhere, for when the account itself is removed. */
    List<Team> findAllByLeadUserId(UUID leadUserId);

    /**
     * Case-folded and space-trimmed, matching the partial unique index behind the table.
     *
     * <p>A derived query cannot express {@code lower(btrim(name))}, and a check that disagreed with
     * the index would report success and then fail on the insert.
     */
    @Query(
            """
            SELECT count(t) > 0 FROM Team t
            WHERE t.workspaceId = :workspaceId
              AND t.deletedAt IS NULL
              AND lower(trim(t.name)) = lower(trim(:name))
              AND (:excludingId IS NULL OR t.id <> :excludingId)
            """)
    boolean existsByFoldedName(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name,
            @Param("excludingId") UUID excludingId);
}
