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

    /** Used when an account is removed, so no roster is left holding a row for somebody gone. */
    long deleteAllByUserId(UUID userId);
}
