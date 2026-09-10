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
}
