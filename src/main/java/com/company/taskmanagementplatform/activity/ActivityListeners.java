package com.company.taskmanagementplatform.activity;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.company.taskmanagementplatform.attachments.AttachmentEvents;
import com.company.taskmanagementplatform.comments.CommentEvents;
import com.company.taskmanagementplatform.common.web.RequestIdFilter;
import com.company.taskmanagementplatform.projects.ProjectEvents;
import com.company.taskmanagementplatform.subtasks.SubtaskEvents;
import com.company.taskmanagementplatform.tasks.TaskEvents;
import com.company.taskmanagementplatform.users.UserAdminEvents;
import com.company.taskmanagementplatform.workspaces.PlatformRoleEvents;
import com.company.taskmanagementplatform.workspaces.RoleEvents;

/**
 * Turns what the other modules announce into audit rows.
 *
 * <p>This class is the whole reason phases three, four and five published events that nothing
 * listened to. Not one of those modules imports this one, and none of them had to be edited to be
 * recorded here.
 *
 * <p><strong>After commit, in a new transaction, on this module's own thread.</strong> An audit row
 * for something that was rolled back would be worse than no audit row at all, so nothing is written
 * until the work is durable. The cost is real and is accepted knowingly: if the write then fails,
 * the record is lost and all that is left is the ERROR line below carrying the correlation id. The
 * alternative, writing inside the caller's transaction, would make an audit failure roll back a
 * person's work, which is the wrong way round for a log that is not a legal record.
 *
 * <p>The separate thread is not an optimisation. A committed transaction has not released its
 * connection when an after-commit listener runs, so writing here would hold a second one, and enough
 * concurrent writers would each hold one while waiting for another until the pool deadlocked. That
 * was a real failure rather than a theoretical one, and {@code ActivityConfig} tells the story.
 *
 * <p>Because the write happens elsewhere, the two things belonging to the request are read here and
 * carried across: the moment it happened, and the correlation id. Reading either on the writing
 * thread would record when the queue got to the row, and a logging context belonging to nobody.
 *
 * <p>Every listener is wrapped, so a defect in here can never surface as a failed request for a
 * change that has already been committed.
 *
 * <p><strong>What is deliberately not recorded:</strong> {@code ProjectProgressChanged}, because
 * progress is derived rather than done by anybody, and an audit trail is a record of what people
 * did. {@code SubtaskCompleted}, because it is the same fact as the status change that produced it
 * and two rows for one action would make a history read like a stutter. {@code UserMentioned}, for
 * the same reason: the mention is part of the comment, and it is carried in that row's metadata.
 */
@Component
class ActivityListeners {

    private static final Logger log = LoggerFactory.getLogger(ActivityListeners.class);

    private final ActivityRecorder recorder;
    private final ThreadPoolTaskExecutor executor;
    private final Clock clock;

    ActivityListeners(
            ActivityRecorder recorder,
            @Qualifier(ActivityConfig.EXECUTOR) ThreadPoolTaskExecutor executor,
            Clock clock) {
        this.recorder = recorder;
        this.executor = executor;
        this.clock = clock;
    }

    // --- projects ---------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectCreated(ProjectEvents.ProjectCreated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.PROJECT_CREATED,
                ActivityEntityType.PROJECT,
                event.projectId(),
                event.projectId(),
                metadata("key", event.key(), "name", event.name()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectUpdated(ProjectEvents.ProjectUpdated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.PROJECT_UPDATED,
                ActivityEntityType.PROJECT,
                event.projectId(),
                event.projectId(),
                Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectStatusChanged(ProjectEvents.ProjectStatusChanged event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.PROJECT_STATUS_CHANGED,
                ActivityEntityType.PROJECT,
                event.projectId(),
                event.projectId(),
                metadata("from", name(event.from()), "to", name(event.to())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectOwnerChanged(ProjectEvents.ProjectOwnerChanged event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.PROJECT_OWNER_CHANGED,
                ActivityEntityType.PROJECT,
                event.projectId(),
                event.projectId(),
                metadata(
                        "previousOwnerUserId",
                        event.previousOwnerUserId(),
                        "newOwnerUserId",
                        event.newOwnerUserId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectMemberAdded(ProjectEvents.ProjectMemberAdded event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.PROJECT_MEMBER_ADDED,
                ActivityEntityType.PROJECT,
                event.projectId(),
                event.projectId(),
                metadata("userId", event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectMemberRemoved(ProjectEvents.ProjectMemberRemoved event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.PROJECT_MEMBER_REMOVED,
                ActivityEntityType.PROJECT,
                event.projectId(),
                event.projectId(),
                metadata("userId", event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onProjectDeleted(ProjectEvents.ProjectDeleted event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.PROJECT_DELETED,
                ActivityEntityType.PROJECT,
                event.projectId(),
                event.projectId(),
                Map.of());
    }

    // --- tasks ------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskCreated(TaskEvents.TaskCreated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.TASK_CREATED,
                ActivityEntityType.TASK,
                event.taskId(),
                event.projectId(),
                metadata(
                        "taskNumber",
                        event.taskNumber(),
                        "title",
                        event.title(),
                        "assigneeUserId",
                        event.assigneeUserId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskUpdated(TaskEvents.TaskUpdated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.TASK_UPDATED,
                ActivityEntityType.TASK,
                event.taskId(),
                event.projectId(),
                Map.of());
    }

    /** Both people, because "assigned to Rahul" is not a useful line on its own. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskAssigned(TaskEvents.TaskAssigned event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.TASK_ASSIGNED,
                ActivityEntityType.TASK,
                event.taskId(),
                event.projectId(),
                metadata(
                        "previousAssigneeUserId",
                        event.previousAssigneeUserId(),
                        "newAssigneeUserId",
                        event.newAssigneeUserId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskStatusChanged(TaskEvents.TaskStatusChanged event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.TASK_STATUS_CHANGED,
                ActivityEntityType.TASK,
                event.taskId(),
                event.projectId(),
                metadata("from", name(event.from()), "to", name(event.to())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskDeleted(TaskEvents.TaskDeleted event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.TASK_DELETED,
                ActivityEntityType.TASK,
                event.taskId(),
                event.projectId(),
                Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskDependencyAdded(TaskEvents.TaskDependencyAdded event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.TASK_DEPENDENCY_ADDED,
                ActivityEntityType.TASK,
                event.taskId(),
                event.projectId(),
                metadata("dependsOnTaskId", event.dependsOnTaskId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTaskDependencyRemoved(TaskEvents.TaskDependencyRemoved event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.TASK_DEPENDENCY_REMOVED,
                ActivityEntityType.TASK,
                event.taskId(),
                event.projectId(),
                metadata("dependsOnTaskId", event.dependsOnTaskId()));
    }

    // --- subtasks ---------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSubtaskCreated(SubtaskEvents.SubtaskCreated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.SUBTASK_CREATED,
                ActivityEntityType.SUBTASK,
                event.subtaskId(),
                event.projectId(),
                metadata("taskId", event.taskId(), "title", event.title()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSubtaskUpdated(SubtaskEvents.SubtaskUpdated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.SUBTASK_UPDATED,
                ActivityEntityType.SUBTASK,
                event.subtaskId(),
                event.projectId(),
                metadata("taskId", event.taskId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSubtaskStatusChanged(SubtaskEvents.SubtaskStatusChanged event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.SUBTASK_STATUS_CHANGED,
                ActivityEntityType.SUBTASK,
                event.subtaskId(),
                event.projectId(),
                metadata("taskId", event.taskId(), "from", name(event.from()), "to", name(event.to())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSubtaskDeleted(SubtaskEvents.SubtaskDeleted event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.SUBTASK_DELETED,
                ActivityEntityType.SUBTASK,
                event.subtaskId(),
                event.projectId(),
                metadata("taskId", event.taskId()));
    }

    // --- comments and attachments ----------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCommentCreated(CommentEvents.CommentCreated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.COMMENT_CREATED,
                ActivityEntityType.COMMENT,
                event.commentId(),
                event.projectId(),
                metadata(
                        "taskId",
                        event.taskId(),
                        "mentionedUserIds",
                        event.mentionedUserIds().isEmpty()
                                ? null
                                : event.mentionedUserIds().stream().map(UUID::toString).toList()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCommentUpdated(CommentEvents.CommentUpdated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.COMMENT_UPDATED,
                ActivityEntityType.COMMENT,
                event.commentId(),
                event.projectId(),
                metadata("taskId", event.taskId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCommentDeleted(CommentEvents.CommentDeleted event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.COMMENT_DELETED,
                ActivityEntityType.COMMENT,
                event.commentId(),
                event.projectId(),
                metadata("taskId", event.taskId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAttachmentUploaded(AttachmentEvents.AttachmentUploaded event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.ATTACHMENT_UPLOADED,
                ActivityEntityType.ATTACHMENT,
                event.attachmentId(),
                event.projectId(),
                metadata("taskId", event.taskId(), "filename", event.filename(), "sizeBytes", event.sizeBytes()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAttachmentDeleted(AttachmentEvents.AttachmentDeleted event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.ATTACHMENT_DELETED,
                ActivityEntityType.ATTACHMENT,
                event.attachmentId(),
                event.projectId(),
                metadata("taskId", event.taskId(), "filename", event.filename()));
    }

    // --- administration ---------------------------------------------------
    //
    // The first rows in the platform written with a null workspace. An account
    // and the platform role belong to the installation rather than to any one
    // workspace, so there is no workspace to name, and V10 dropped the NOT NULL
    // that used to make them impossible to record at all.
    //
    // This is where architecture.md's standing promise about the platform
    // administrator - "every action it takes is audited from the phase that
    // adds auditing" - actually falls due. Phase six could not meet it for
    // these, because none of them had an endpoint.

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserProfileUpdated(UserAdminEvents.ProfileUpdated event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_PROFILE_UPDATED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                metadata("firstName", event.firstName(), "lastName", event.lastName()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserActivated(UserAdminEvents.Activated event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_ACTIVATED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                metadata("status", name(event.status())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserDeactivated(UserAdminEvents.Deactivated event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_DEACTIVATED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                Map.of());
    }

    /**
     * The address is taken from the event rather than looked up.
     *
     * <p>By the time this runs the account is soft-deleted and its memberships are gone, so anything
     * this row wants had to be read before the event was published. That ordering is the rule
     * {@code UserAdminEvents} records, and it is the failure that would appear only for somebody who
     * actually belonged to something.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserDeleted(UserAdminEvents.Deleted event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_DELETED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                metadata("email", event.email()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserApproved(UserAdminEvents.Approved event) {
        // Who let somebody into the platform is the first question asked when an
        // account turns out to have reach it should not, so it is audited in its
        // own right rather than left implicit in the membership that follows.
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_APPROVED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserUnlocked(UserAdminEvents.Unlocked event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_UNLOCKED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                Map.of());
    }

    /** The token is not in the event and is not in the row. Only that a recovery was started. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPasswordResetRequested(UserAdminEvents.PasswordResetRequested event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_PASSWORD_RESET_REQUESTED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onVerificationResent(UserAdminEvents.VerificationResent event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.USER_VERIFICATION_RESENT,
                ActivityEntityType.USER,
                event.userId(),
                null,
                Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPlatformRoleGranted(PlatformRoleEvents.Granted event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.PLATFORM_ROLE_GRANTED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPlatformRoleRevoked(PlatformRoleEvents.Revoked event) {
        record(
                null,
                event.actorUserId(),
                ActivityActions.PLATFORM_ROLE_REVOKED,
                ActivityEntityType.USER,
                event.userId(),
                null,
                Map.of());
    }

    /**
     * The one administrative row that names a workspace, because a role belongs to one.
     *
     * <p>It therefore appears in that workspace's own history rather than in the platform trail,
     * which is where somebody wondering why their permissions changed this morning would look.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRolePermissionsChanged(RoleEvents.PermissionsChanged event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                ActivityActions.ROLE_PERMISSIONS_CHANGED,
                ActivityEntityType.ROLE,
                event.roleId(),
                null,
                metadata(
                        "roleSlug",
                        event.roleSlug(),
                        "added",
                        event.added().isEmpty() ? null : event.added(),
                        "removed",
                        event.removed().isEmpty() ? null : event.removed()));
    }

    // --- plumbing ---------------------------------------------------------

    /**
     * Queues the row, and never lets a failure here become the caller's problem.
     *
     * <p>The work has already committed by the time this runs, so throwing would produce a 500 for a
     * change that did in fact happen. Both failures that can occur are logged with everything needed
     * to write the row by hand: a full queue, which means the database is not keeping up, and a
     * failed write.
     */
    private void record(
            UUID workspaceId,
            UUID actorUserId,
            String action,
            ActivityEntityType entityType,
            UUID entityId,
            UUID projectId,
            Map<String, Object> metadata) {

        // Read on this thread, which is still the request's. Both belong to it.
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        Instant occurredAt = clock.instant();

        try {
            executor.execute(() -> write(
                    workspaceId,
                    actorUserId,
                    action,
                    entityType,
                    entityId,
                    projectId,
                    metadata,
                    requestId,
                    occurredAt));
        } catch (TaskRejectedException e) {
            log.error(
                    "Dropped activity, the write queue is full: action={} entityType={} entityId={} "
                            + "workspaceId={} actorUserId={} requestId={}",
                    action,
                    entityType,
                    entityId,
                    workspaceId,
                    actorUserId,
                    requestId,
                    e);
        }
    }

    private void write(
            UUID workspaceId,
            UUID actorUserId,
            String action,
            ActivityEntityType entityType,
            UUID entityId,
            UUID projectId,
            Map<String, Object> metadata,
            String requestId,
            Instant occurredAt) {

        try {
            recorder.write(
                    workspaceId,
                    actorUserId,
                    action,
                    entityType,
                    entityId,
                    projectId,
                    metadata,
                    requestId,
                    occurredAt);
        } catch (RuntimeException e) {
            log.error(
                    "Could not write activity: action={} entityType={} entityId={} workspaceId={} "
                            + "actorUserId={} requestId={}",
                    action,
                    entityType,
                    entityId,
                    workspaceId,
                    actorUserId,
                    requestId,
                    e);
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
}
