package com.company.taskmanagementplatform.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Page<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId, Pageable pageable);

    List<WorkspaceMember> findAllByUserId(UUID userId);

    long countByWorkspaceIdAndRoleId(UUID workspaceId, UUID roleId);

    /** The whole roster size, for the administrator's dashboard. */
    long countByWorkspaceId(UUID workspaceId);

    /**
     * The roster size split by role, in one query.
     *
     * @return rows of {@code [roleId, count]}
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT m.roleId, count(m) FROM WorkspaceMember m WHERE m.workspaceId = :workspaceId GROUP BY m.roleId")
    List<Object[]> countByWorkspaceIdGroupedByRole(
            @org.springframework.data.repository.query.Param("workspaceId") UUID workspaceId);

    /**
     * How many workspaces each of these people belongs to, in one query.
     *
     * <p>For the administrative account listing, which shows it beside each row. One grouped query
     * for the page rather than one per row, which is how every listing in this platform is written.
     *
     * @return rows of {@code [userId, count]}
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT m.userId, count(m) FROM WorkspaceMember m WHERE m.userId IN :userIds GROUP BY m.userId")
    List<Object[]> countByUserIdsGrouped(
            @org.springframework.data.repository.query.Param("userIds") java.util.Collection<UUID> userIds);

    /** Used when an account is removed, so no roster is left holding a row for somebody gone. */
    long deleteAllByUserId(UUID userId);
}
