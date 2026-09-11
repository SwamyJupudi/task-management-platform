package com.company.taskmanagementplatform.notifications;

/**
 * The seven things this platform tells somebody about.
 *
 * <p>Six are the requirements' own list, word for word: a task assigned or its status changed, a
 * user mentioned, a deadline approaching, a user added to a project, a comment added, and a project
 * status changed. The seventh is not an addition: "task assigned or status changes" is one line in
 * the document and two events here, because they reach different people for different reasons.
 *
 * <p>The wire value is the lower-case dotted code rather than the enum name, and it is stored that
 * way too. It matches the action codes the audit trail uses, so a client that already knows {@code
 * task.status_changed} from one feed does not have to learn {@code TASK_STATUS_CHANGED} for the
 * other.
 *
 * <p>Nothing is added here without widening the check constraint in the migration that owns the
 * table, which is why the constraint lists the codes rather than trusting the application.
 */
enum NotificationType {
    TASK_ASSIGNED("task.assigned", NotificationEntityType.TASK),
    TASK_STATUS_CHANGED("task.status_changed", NotificationEntityType.TASK),
    TASK_DEADLINE_APPROACHING("task.deadline_approaching", NotificationEntityType.TASK),
    COMMENT_CREATED("comment.created", NotificationEntityType.COMMENT),
    COMMENT_MENTIONED("comment.mentioned", NotificationEntityType.COMMENT),
    PROJECT_MEMBER_ADDED("project.member_added", NotificationEntityType.PROJECT),
    PROJECT_STATUS_CHANGED("project.status_changed", NotificationEntityType.PROJECT);

    private final String code;
    private final NotificationEntityType entityType;

    NotificationType(String code, NotificationEntityType entityType) {
        this.code = code;
        this.entityType = entityType;
    }

    String code() {
        return code;
    }

    /** What the row points at, which is fixed per type rather than decided at the call site. */
    NotificationEntityType entityType() {
        return entityType;
    }
}
