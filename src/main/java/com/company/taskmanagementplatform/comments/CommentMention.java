package com.company.taskmanagementplatform.comments;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One person named in one comment.
 *
 * <p>A join table, so not soft-deleted, per the convention. The rows are derived from the body by
 * {@link MentionParser} and rewritten whenever the body is: a mention added by an edit is a new row
 * and will notify in phase seven, and one removed by an edit is gone and never notifies again.
 *
 * <p>The composite key is the pair itself rather than a surrogate, because naming the same person
 * twice in one comment is one mention, not two, and the database is the right place to say so.
 */
@Entity
@Table(name = "comment_mentions")
class CommentMention {

    @EmbeddedId
    private Id id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentMention() {
        // for JPA
    }

    CommentMention(UUID commentId, UUID mentionedUserId, UUID workspaceId) {
        this.id = new Id(commentId, mentionedUserId);
        this.workspaceId = workspaceId;
    }

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
    }

    UUID getCommentId() {
        return id.commentId;
    }

    UUID getMentionedUserId() {
        return id.mentionedUserId;
    }

    @Embeddable
    static class Id implements Serializable {

        @Column(name = "comment_id", nullable = false, updatable = false)
        private UUID commentId;

        @Column(name = "mentioned_user_id", nullable = false, updatable = false)
        private UUID mentionedUserId;

        protected Id() {
            // for JPA
        }

        Id(UUID commentId, UUID mentionedUserId) {
            this.commentId = commentId;
            this.mentionedUserId = mentionedUserId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(commentId, that.commentId)
                    && Objects.equals(mentionedUserId, that.mentionedUserId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(commentId, mentionedUserId);
        }
    }
}
