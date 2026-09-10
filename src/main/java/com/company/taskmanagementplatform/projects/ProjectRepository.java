package com.company.taskmanagementplatform.projects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private, like every repository in the platform. No module outside reaches this table. */
interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    /**
     * Always by workspace as well as by identifier.
     *
     * <p>There is no lookup here that takes a project identifier alone. A project from another
     * workspace has to be indistinguishable from one that never existed, and the surest way to
     * guarantee that is to make the narrower question the only one the repository can answer.
     */
    Optional<Project> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    /** Every live project owned by this person, for the cleanup that runs when they leave. */
    List<Project> findAllByWorkspaceIdAndOwnerUserIdAndDeletedAtIsNull(UUID workspaceId, UUID ownerUserId);

    /** Every live project owned by this person anywhere, for when the account itself is removed. */
    List<Project> findAllByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId);

    /** Every live project pointing at this team, so a deleted team leaves nothing dangling. */
    List<Project> findAllByWorkspaceIdAndTeamIdAndDeletedAtIsNull(UUID workspaceId, UUID teamId);

    /**
     * Case-folded and space-trimmed, matching the partial unique indexes behind the table.
     *
     * <p>A derived query cannot express {@code lower(btrim(name))}, and a check that disagreed with
     * the index would report success and then fail on the insert.
     */
    @Query(
            """
            SELECT count(p) > 0 FROM Project p
            WHERE p.workspaceId = :workspaceId
              AND p.deletedAt IS NULL
              AND lower(trim(p.name)) = lower(trim(:name))
              AND (:excludingId IS NULL OR p.id <> :excludingId)
            """)
    boolean existsByFoldedName(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name,
            @Param("excludingId") UUID excludingId);

    @Query(
            """
            SELECT count(p) > 0 FROM Project p
            WHERE p.workspaceId = :workspaceId
              AND p.deletedAt IS NULL
              AND upper(trim(p.key)) = upper(trim(:key))
              AND (:excludingId IS NULL OR p.id <> :excludingId)
            """)
    boolean existsByFoldedKey(
            @Param("workspaceId") UUID workspaceId,
            @Param("key") String key,
            @Param("excludingId") UUID excludingId);
}
