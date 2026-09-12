package com.company.taskmanagementplatform.workspaces;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every repository here. No module outside reaches these tables. */
interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    Page<Workspace> findAllByDeletedAtIsNull(Pageable pageable);

    long countByDeletedAtIsNull();

    /** Live workspaces grouped by status, for the platform statistics. Rows of {@code [status, count]}. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT w.status, count(w) FROM Workspace w WHERE w.deletedAt IS NULL GROUP BY w.status")
    java.util.List<Object[]> countByStatusGrouped();

    /** A named set, for resolving workspace names across a page of projects in one query. */
    java.util.List<Workspace> findAllByIdInAndDeletedAtIsNull(java.util.Collection<UUID> ids);
}
