package com.company.taskmanagementplatform.attachments;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.comments.CommentEvents;
import com.company.taskmanagementplatform.projects.ProjectEvents;
import com.company.taskmanagementplatform.tasks.TaskEvents;

/**
 * Takes a task's files with it when the task, its comment or its project goes away.
 *
 * <p>Like {@code CommentCleanupListener}, there is deliberately nothing here for somebody leaving a
 * workspace or a project. The uploader is keyed to {@code users}, not to a membership row, so
 * nothing has to be stood down and a file outlives the person who added it changing team.
 *
 * <p><strong>None of this deletes bytes.</strong> The rows are soft-deleted and the stored objects
 * stay where they are, which is the price of a soft delete meaning something. The purge that
 * reclaims the storage is hardening-phase work, and until it exists the store grows. That is stated
 * in {@code architecture.md} rather than left for somebody to discover from a storage bill.
 */
@Component
class AttachmentCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupListener.class);

    private final AttachmentRepository attachments;
    private final Clock clock;

    AttachmentCleanupListener(AttachmentRepository attachments, Clock clock) {
        this.attachments = attachments;
        this.clock = clock;
    }

    /** A comment was removed, so the files that belonged to it go with it. */
    @EventListener
    @Transactional
    void onCommentDeleted(CommentEvents.CommentDeleted event) {
        softDelete(
                attachments.findAllByCommentIdAndDeletedAtIsNull(event.commentId()), "comment", event.commentId());
    }

    @EventListener
    @Transactional
    void onTaskDeleted(TaskEvents.TaskDeleted event) {
        softDelete(attachments.findAllByTaskIdAndDeletedAtIsNull(event.taskId()), "task", event.taskId());
    }

    /** Directly rather than through each task's deletion, for the reason the subtasks listener gives. */
    @EventListener
    @Transactional
    void onProjectDeleted(ProjectEvents.ProjectDeleted event) {
        softDelete(
                attachments.findAllByProjectIdAndDeletedAtIsNull(event.projectId()), "project", event.projectId());
    }

    private void softDelete(List<Attachment> found, String parentKind, UUID parentId) {
        if (found.isEmpty()) {
            return;
        }
        found.forEach(attachment -> attachment.softDelete(clock.instant()));
        attachments.flush();
        log.info("Removed {} attachment(s) with a deleted {}: id={}", found.size(), parentKind, parentId);
    }
}
