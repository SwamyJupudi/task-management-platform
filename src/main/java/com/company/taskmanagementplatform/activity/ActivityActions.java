package com.company.taskmanagementplatform.activity;

/**
 * The vocabulary of recorded actions.
 *
 * <p>Constants rather than an enum, because these are stored strings that outlive the code that
 * writes them. An audit row written in 2026 has to stay readable after a later phase stops
 * publishing that action, and an enum would turn a historical row into a deserialisation failure.
 *
 * <p>The form is {@code entity.what_happened}, lower case, so a reader can group by prefix and a
 * later filter can match on one.
 */
final class ActivityActions {

    static final String PROJECT_CREATED = "project.created";
    static final String PROJECT_UPDATED = "project.updated";
    static final String PROJECT_STATUS_CHANGED = "project.status_changed";
    static final String PROJECT_OWNER_CHANGED = "project.owner_changed";
    static final String PROJECT_MEMBER_ADDED = "project.member_added";
    static final String PROJECT_MEMBER_REMOVED = "project.member_removed";
    static final String PROJECT_DELETED = "project.deleted";

    static final String TASK_CREATED = "task.created";
    static final String TASK_UPDATED = "task.updated";
    static final String TASK_ASSIGNED = "task.assigned";
    static final String TASK_STATUS_CHANGED = "task.status_changed";
    static final String TASK_DELETED = "task.deleted";
    static final String TASK_DEPENDENCY_ADDED = "task.dependency_added";
    static final String TASK_DEPENDENCY_REMOVED = "task.dependency_removed";

    static final String SUBTASK_CREATED = "subtask.created";
    static final String SUBTASK_UPDATED = "subtask.updated";
    static final String SUBTASK_STATUS_CHANGED = "subtask.status_changed";
    static final String SUBTASK_DELETED = "subtask.deleted";

    static final String COMMENT_CREATED = "comment.created";
    static final String COMMENT_UPDATED = "comment.updated";
    static final String COMMENT_DELETED = "comment.deleted";

    static final String ATTACHMENT_UPLOADED = "attachment.uploaded";
    static final String ATTACHMENT_DELETED = "attachment.deleted";

    private ActivityActions() {}
}
