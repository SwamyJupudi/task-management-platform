package com.company.taskmanagementplatform.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.company.taskmanagementplatform.comments.CommentEvents;
import com.company.taskmanagementplatform.notifications.NotificationWriter.NotificationRow;
import com.company.taskmanagementplatform.projects.ProjectEvents;
import com.company.taskmanagementplatform.tasks.TaskEvents;

/**
 * Turns what the other modules announce into notifications.
 *
 * <p>This class is the second reason phases three to six published events that nothing listened to.
 * Five of the six triggers the requirements name were already being announced with a comment saying
 * phase seven would hear them, and not one of those modules is imported by anything here or has been
 * edited to make this work. The sixth, the approaching deadline, has no event to listen for because
 * nobody does it; it is {@link DeadlineScanner}'s.
 *
 * <p><strong>After commit, in a new transaction, on this module's own thread.</strong> A message
 * about something that was rolled back would be worse than no message, so nothing is sent until the
 * work is durable, and the separate thread is what stops a writing request from holding two database
 * connections at once. {@link NotificationConfig} tells that story; it was a real failure in phase
 * six rather than a theoretical one.
 *
 * <p>Every listener is wrapped, so a defect in here can never surface as a failed request for a
 * change that has already been committed. The cost is accepted knowingly: a failure here loses the
 * notification and leaves the ERROR line below with the correlation to find it by.
 *
 * <p><strong>What is deliberately not a trigger:</strong> {@code ProjectProgressChanged}, because
 * nobody performed it and the requirements do not name it. {@code TaskCreated}, {@code TaskUpdated},
 * {@code TaskDeleted} and every subtask and attachment event, for the same reason: the document
 * lists six triggers, and a feed that announced everything would be one nobody reads.
 */
@Component
class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    private final RecipientResolver recipients;
    private final NotificationWriter writer;
    private final ThreadPoolTaskExecutor executor;
    private final Clock clock;

    NotificationListeners(
            RecipientResolver recipients,
            NotificationWriter writer,
            @Qualifier(NotificationConfig.EXECUTOR) ThreadPoolTaskExecutor executor,
            Clock clock) {
        this.recipients = recipients;
        this.writer = writer;
        this.executor = executor;
        this.clock = clock;
    }

    // --- tasks ------------------------------------------------------------

    /**
     * The new assignee, and nobody else.
     *
     * <p>The person the task was taken away from is not told. The requirements name "task assigned"
     * as the trigger, and a message saying something is no longer yours is a different feature with a
     * different purpose that nothing asked for.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskAssigned(TaskEvents.TaskAssigned event) {
        queue(() -> RecipientResolver.only(event.newAssigneeUserId(), event.actorUserId()).stream()
                .map(recipient -> NotificationRow.of(
                        event.workspaceId(),
                        recipient,
                        event.actorUserId(),
                        NotificationType.TASK_ASSIGNED,
                        event.taskId(),
                        event.projectId(),
                        metadata("previousAssigneeUserId", event.previousAssigneeUserId())))
                .toList());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskStatusChanged(TaskEvents.TaskStatusChanged event) {
        queue(() -> NotificationRow.forEach(
                recipients.forTask(event.workspaceId(), event.taskId(), event.actorUserId()),
                event.workspaceId(),
                event.actorUserId(),
                NotificationType.TASK_STATUS_CHANGED,
                event.taskId(),
                event.projectId(),
                metadata("from", name(event.from()), "to", name(event.to()))));
    }

    // --- comments ---------------------------------------------------------

    /**
     * The task's people, minus anybody the same comment already named.
     *
     * <p>Rule three: a mention beats the comment it is in. Being told twice about one comment, once
     * because somebody typed your name and once because a comment exists, is the fastest way to
     * teach people to ignore the feed.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCommentCreated(CommentEvents.CommentCreated event) {
        queue(() -> NotificationRow.forEach(
                recipients.forTask(
                        event.workspaceId(),
                        event.taskId(),
                        event.actorUserId(),
                        event.mentionedUserIds().toArray(UUID[]::new)),
                event.workspaceId(),
                event.actorUserId(),
                NotificationType.COMMENT_CREATED,
                event.commentId(),
                event.projectId(),
                metadata("taskId", event.taskId())));
    }

    /**
     * One person named in one comment.
     *
     * <p>Published per person by the comments module rather than as a list on the comment, which is
     * why this listener has no unpacking to do. An edit publishes it only for names the edit newly
     * added, so fixing a typo does not tell everybody again.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserMentioned(CommentEvents.UserMentioned event) {
        queue(() -> RecipientResolver.only(event.mentionedUserId(), event.actorUserId()).stream()
                .map(recipient -> NotificationRow.of(
                        event.workspaceId(),
                        recipient,
                        event.actorUserId(),
                        NotificationType.COMMENT_MENTIONED,
                        event.commentId(),
                        event.projectId(),
                        metadata("taskId", event.taskId())))
                .toList());
    }

    // --- projects ---------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectMemberAdded(ProjectEvents.ProjectMemberAdded event) {
        queue(() -> RecipientResolver.only(event.userId(), event.actorUserId()).stream()
                .map(recipient -> NotificationRow.of(
                        event.workspaceId(),
                        recipient,
                        event.actorUserId(),
                        NotificationType.PROJECT_MEMBER_ADDED,
                        event.projectId(),
                        event.projectId(),
                        Map.of()))
                .toList());
    }

    /**
     * Everybody on the project, which is the one trigger that fans out.
     *
     * <p>Bounded by the project's own membership rather than by the workspace, and written in one
     * transaction, so a project of forty people is forty rows and one commit.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectStatusChanged(ProjectEvents.ProjectStatusChanged event) {
        queue(() -> NotificationRow.forEach(
                recipients.forProject(event.workspaceId(), event.projectId(), event.actorUserId()),
                event.workspaceId(),
                event.actorUserId(),
                NotificationType.PROJECT_STATUS_CHANGED,
                event.projectId(),
                event.projectId(),
                metadata("from", name(event.from()), "to", name(event.to()))));
    }

    // --- plumbing ---------------------------------------------------------

    /**
     * Queues the work, and never lets a failure here become the caller's problem.
     *
     * <p>The recipients are resolved on the writing thread rather than here, because resolving them
     * is a database read and doing it in an after-commit listener is exactly what would make a
     * request hold a second connection.
     */
    private void queue(RowSupplier rows) {
        Instant occurredAt = clock.instant();

        try {
            executor.execute(() -> write(rows, occurredAt));
        } catch (TaskRejectedException e) {
            log.error("Dropped notifications, the write queue is full", e);
        }
    }

    private void write(RowSupplier rows, Instant occurredAt) {
        try {
            List<NotificationRow> resolved = rows.get();
            if (!resolved.isEmpty()) {
                writer.writeAll(resolved, occurredAt);
            }
        } catch (RuntimeException e) {
            log.error("Could not write notifications", e);
        }
    }

    /** A small ordered map that tolerates the nulls {@code Map.of} refuses. */
    private static Map<String, Object> metadata(Object... keysAndValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            metadata.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return metadata;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /** Deferred row building, so the reads it needs happen on the writing thread. */
    @FunctionalInterface
    private interface RowSupplier {
        List<NotificationRow> get();
    }
}
