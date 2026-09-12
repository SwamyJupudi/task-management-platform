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

    // --- administration, phase nine ---------------------------------------
    //
    // These are the first actions recorded with no workspace. An account and a
    // platform role belong to the installation rather than to any one
    // workspace, so their rows carry a null workspace_id and are read by the
    // platform browse rather than by a workspace's own history.
    //
    // role.permissions_changed is the exception: a role belongs to a workspace,
    // so that row carries one and appears in that workspace's history, which is
    // where somebody wondering why their permissions changed would look.

    static final String USER_PROFILE_UPDATED = "user.profile_updated";
    static final String USER_ACTIVATED = "user.activated";
    static final String USER_DEACTIVATED = "user.deactivated";
    static final String USER_DELETED = "user.deleted";
    static final String USER_UNLOCKED = "user.unlocked";
    static final String USER_PASSWORD_RESET_REQUESTED = "user.password_reset_requested";
    static final String USER_VERIFICATION_RESENT = "user.verification_resent";

    static final String PLATFORM_ROLE_GRANTED = "platform_role.granted";
    static final String PLATFORM_ROLE_REVOKED = "platform_role.revoked";

    static final String ROLE_PERMISSIONS_CHANGED = "role.permissions_changed";

    private ActivityActions() {}
}
