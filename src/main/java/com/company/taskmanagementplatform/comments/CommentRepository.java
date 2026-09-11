package com.company.taskmanagementplatform.comments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every repository in the platform. No module outside reaches this table. */
interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Always by workspace as well as by identifier.
     *
     * <p>The workspace is part of the lookup rather than checked afterwards, so a comment identifier
     * from another workspace is indistinguishable from one that was never real.
     */
    Optional<Comment> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    Page<Comment> findAllByTaskIdAndDeletedAtIsNull(UUID taskId, Pageable pageable);

    /** For the cascade when the parent task, or its whole project, goes away. */
    List<Comment> findAllByTaskIdAndDeletedAtIsNull(UUID taskId);

    List<Comment> findAllByProjectIdAndDeletedAtIsNull(UUID projectId);
}
