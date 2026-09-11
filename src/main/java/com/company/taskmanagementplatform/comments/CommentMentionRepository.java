package com.company.taskmanagementplatform.comments;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private. Phase seven reads mentions through this module's service, not through here. */
interface CommentMentionRepository extends JpaRepository<CommentMention, CommentMention.Id> {

    List<CommentMention> findAllByIdCommentId(UUID commentId);

    List<CommentMention> findAllByIdCommentIdIn(Collection<UUID> commentIds);

    void deleteAllByIdCommentId(UUID commentId);
}
