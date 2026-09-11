package com.company.taskmanagementplatform.comments;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.projects.ProjectEvents;
import com.company.taskmanagementplatform.tasks.TaskEvents;

/**
 * Takes a task's discussion with it when the task goes away.
 *
 * <p><strong>There is deliberately no listener here for somebody leaving a workspace, leaving a
 * project, or having their account removed.</strong> Every other module built so far needs one,
 * because its people columns are foreign keys into a membership table and the database refuses the
 * removal until they are cleared. A comment's author is keyed to {@code users} instead, whose rows
 * are only ever soft-deleted, so nothing has to be stood down and, more importantly, nothing should
 * be: a comment has to outlive its author changing team. This is the first module in the platform
 * that needs no cleanup on a membership change, and the schema is what makes that true rather than a
 * choice this class makes.
 *
 * <p>What is left is the cascade. A soft-deleted task must take its thread with it, or the comments
 * would be reachable through a parent no read can see.
 */
@Component
class CommentCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(CommentCleanupListener.class);

    private final CommentRepository comments;
    private final CommentMentionRepository mentions;
    private final Clock clock;

    CommentCleanupListener(CommentRepository comments, CommentMentionRepository mentions, Clock clock) {
        this.comments = comments;
        this.mentions = mentions;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    void onTaskDeleted(TaskEvents.TaskDeleted event) {
        softDelete(comments.findAllByTaskIdAndDeletedAtIsNull(event.taskId()), "task", event.taskId());
    }

    /**
     * A project was removed, so every discussion inside it goes too.
     *
     * <p>Listened for directly rather than through the per-task deletions, for the reason {@code
     * SubtaskCleanupListener} gives: one project can hold thousands of tasks, and a burst of one
     * event each would be a needless storm for anything listening in a later phase.
     */
    @EventListener
    @Transactional
    void onProjectDeleted(ProjectEvents.ProjectDeleted event) {
        softDelete(comments.findAllByProjectIdAndDeletedAtIsNull(event.projectId()), "project", event.projectId());
    }

    private void softDelete(List<Comment> found, String parentKind, UUID parentId) {
        if (found.isEmpty()) {
            return;
        }

        found.forEach(comment -> {
            comment.softDelete(clock.instant());
            // Nothing should be notified about a comment nobody can read.
            mentions.deleteAllByIdCommentId(comment.getId());
        });
        comments.flush();

        log.info("Removed {} comment(s) with a deleted {}: id={}", found.size(), parentKind, parentId);
    }
}
