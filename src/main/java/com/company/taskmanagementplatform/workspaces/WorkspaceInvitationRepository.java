package com.company.taskmanagementplatform.workspaces;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    /** Lookup is by hash, because the token itself was never stored. */
    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailAndStatus(
            UUID workspaceId, String email, InvitationStatus status);

    Page<WorkspaceInvitation> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
}
