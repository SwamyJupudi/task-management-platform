package com.company.taskmanagementplatform.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    Optional<Role> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Role> findAllByWorkspaceIdOrderBySlugAsc(UUID workspaceId);

    /** Platform roles have no workspace. There is one of them today, seeded by a migration. */
    Optional<Role> findByWorkspaceIdIsNullAndSlug(String slug);
}
