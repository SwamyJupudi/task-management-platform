package com.company.taskmanagementplatform.projects;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    Page<ProjectMember> findAllByProjectId(UUID projectId, Pageable pageable);

    long countByProjectId(UUID projectId);

    /**
     * Sizes for a whole page of projects in one query.
     *
     * <p>A count per row is exactly the shape that turns a list endpoint into N+1 without anybody
     * noticing until it is in production.
     *
     * @return rows of {@code [projectId, count]}
     */
    @Query("SELECT pm.projectId, count(pm) FROM ProjectMember pm WHERE pm.projectId IN :projectIds GROUP BY pm.projectId")
    List<Object[]> countByProjectIds(@Param("projectIds") Collection<UUID> projectIds);

    /** Used when somebody leaves a workspace: every project of that workspace loses them at once. */
    long deleteAllByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /** Used when an account is removed altogether. */
    long deleteAllByUserId(UUID userId);

    /** Everybody on one project, for the notification that goes to all of them. */
    @Query("SELECT pm.userId FROM ProjectMember pm WHERE pm.projectId = :projectId")
    List<UUID> findUserIdsByProjectId(@Param("projectId") UUID projectId);
}
