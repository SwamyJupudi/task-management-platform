package com.company.taskmanagementplatform.projects;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * The projects one person owns or belongs to, for the read scope tasks inherit.
     *
     * <p>Identifiers rather than entities. The caller turns them into a predicate; loading the rows
     * would be a page of projects nobody asked for.
     */
    @Query(
            """
            SELECT p.id FROM Project p
            WHERE p.workspaceId = :workspaceId
              AND p.deletedAt IS NULL
              AND (p.ownerUserId = :userId
                   OR EXISTS (SELECT 1 FROM ProjectMember m
                              WHERE m.projectId = p.id AND m.userId = :userId))
            """)
    List<UUID> findIdsOwnedOrJoinedBy(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    /**
     * The projects of one or more teams.
     *
     * <p>Serves both halves of the third way a project comes into reach, and the {@code teamId}
     * filter on a task listing, which cannot be a column on the task itself: a project may change
     * teams, and a copy of the team on every task would be wrong from that moment on.
     */
    @Query(
            """
            SELECT p.id FROM Project p
            WHERE p.workspaceId = :workspaceId
              AND p.deletedAt IS NULL
              AND p.teamId IN :teamIds
            """)
    List<UUID> findIdsByTeamIds(@Param("workspaceId") UUID workspaceId, @Param("teamIds") Collection<UUID> teamIds);

    /** The identifier behind a project key, so a search for {@code PROJ-12} resolves without a join. */
    @Query(
            """
            SELECT p.id FROM Project p
            WHERE p.workspaceId = :workspaceId
              AND p.deletedAt IS NULL
              AND upper(trim(p.key)) = upper(trim(:key))
            """)
    List<UUID> findIdsByFoldedKey(@Param("workspaceId") UUID workspaceId, @Param("key") String key);

    /**
     * The fields another module needs to authorize inside a project, as values rather than an entity.
     *
     * <p>A projection on purpose. An entity loaded here would join the persistence context, and the
     * progress statement below writes the same row behind Hibernate's back; a managed copy sitting
     * beside it is exactly the setup in which one of the two silently wins.
     *
     * @return at most one row of {@code [id, workspaceId, key, name, status, ownerUserId, teamId]}
     */
    @Query(
            """
            SELECT p.id, p.workspaceId, p.key, p.name, p.status, p.ownerUserId, p.teamId
            FROM Project p
            WHERE p.id = :projectId AND p.workspaceId = :workspaceId AND p.deletedAt IS NULL
            """)
    List<Object[]> findSummary(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId);

    /**
     * Derives progress from the project's tasks, in one statement.
     *
     * <p>One statement rather than a read, a calculation and a write, so two people completing tasks
     * in the same project at the same time cannot lose each other's update and no row has to be
     * locked. The cost is the one boundary this platform crosses deliberately: the subquery names
     * the task tables, which the tasks module owns. The alternative was to compute the number in
     * {@code tasks} and hand it here, which needs a lock to be correct and can still leave a
     * permanently stale value if the loser of a race is the last writer.
     *
     * <p>The rule, which {@code database.md} records: a task counts one when it is DONE, otherwise
     * the share of its live subtasks that are done, otherwise zero. A task marked done is done, so
     * DONE wins over an unfinished checklist rather than being averaged with it. Deleted tasks and
     * deleted subtasks leave both sides of the fraction. A project with no live tasks reads zero.
     *
     * <p>{@code updated_at} is deliberately not touched. Progress is derived, and bumping the
     * project's timestamp every time somebody moves a card would make "last edited" meaningless.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    UPDATE projects p
                    SET progress = COALESCE((
                            SELECT least(100, greatest(0, floor(
                                       100.0 * COALESCE(sum(
                                           CASE
                                               WHEN t.status = 'DONE' THEN 1.0
                                               WHEN st.total > 0 THEN st.done::numeric / st.total
                                               ELSE 0.0
                                           END), 0) / nullif(count(*), 0))::int))
                            FROM tasks t
                            LEFT JOIN LATERAL (
                                SELECT count(*) AS total,
                                       count(*) FILTER (WHERE s.status = 'DONE') AS done
                                FROM subtasks s
                                WHERE s.task_id = t.id AND s.deleted_at IS NULL
                            ) st ON true
                            WHERE t.project_id = p.id AND t.deleted_at IS NULL
                        ), 0)
                    WHERE p.id = :projectId AND p.workspace_id = :workspaceId AND p.deleted_at IS NULL
                    """)
    int recalculateProgress(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId);

    /** Reads the stored value back, for the event that announces a change to it. */
    @Query("SELECT p.progress FROM Project p WHERE p.id = :projectId AND p.workspaceId = :workspaceId")
    List<Integer> findProgress(@Param("workspaceId") UUID workspaceId, @Param("projectId") UUID projectId);

    /**
     * The keys of a set of projects, so a page of notifications can render "PROJ-12" without a
     * lookup per row.
     *
     * @return rows of {@code [id, key]}
     */
    @Query("SELECT p.id, p.key FROM Project p WHERE p.id IN :projectIds")
    List<Object[]> findKeys(@Param("projectIds") java.util.Collection<UUID> projectIds);
}
