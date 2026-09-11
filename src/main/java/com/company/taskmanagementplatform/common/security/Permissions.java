package com.company.taskmanagementplatform.common.security;

import java.util.Set;

/**
 * Every permission code the application implements, as constants.
 *
 * <p>The codes also exist as rows in the {@code permissions} table, written by a migration. This
 * class is the compile-time half of that pair: an annotation referring to a code that was never
 * seeded is a bug nothing else would catch, so {@code PermissionCatalogIT} asserts the two sides
 * agree.
 *
 * <p>Adding a code here means adding it to a migration, and mapping it to {@code SUPER_ADMIN} in
 * that same migration. There is no bypass branch for the platform administrator, so an unmapped
 * permission is one it does not hold.
 */
public final class Permissions {

    public static final String USER_READ = "user:read";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_ACTIVATE = "user:activate";
    public static final String USER_DEACTIVATE = "user:deactivate";
    public static final String USER_DELETE = "user:delete";

    public static final String WORKSPACE_READ = "workspace:read";
    public static final String WORKSPACE_CREATE = "workspace:create";
    public static final String WORKSPACE_UPDATE = "workspace:update";
    public static final String WORKSPACE_DELETE = "workspace:delete";
    public static final String WORKSPACE_ARCHIVE = "workspace:archive";

    public static final String MEMBER_READ = "member:read";
    public static final String MEMBER_INVITE = "member:invite";
    public static final String MEMBER_REMOVE = "member:remove";
    public static final String MEMBER_ASSIGN_ROLE = "member:assign_role";

    public static final String ROLE_READ = "role:read";
    public static final String ROLE_MANAGE = "role:manage";

    public static final String PERMISSION_READ = "permission:read";

    public static final String TEAM_READ = "team:read";
    public static final String TEAM_CREATE = "team:create";
    public static final String TEAM_UPDATE = "team:update";
    public static final String TEAM_DELETE = "team:delete";
    public static final String TEAM_MANAGE_MEMBERS = "team:manage_members";

    /**
     * The workspace-wide grant that widens the two team-management codes above from "the teams I
     * lead" to "every team here".
     *
     * <p>This is the scope half of the two-layer rule in {@code architecture.md}. Holding {@link
     * #TEAM_UPDATE} answers what the caller may do; holding this as well answers which teams they may
     * do it to. A team lead holds the first and not the second.
     */
    public static final String TEAM_MANAGE_ANY = "team:manage_any";

    public static final String PROJECT_READ = "project:read";
    public static final String PROJECT_CREATE = "project:create";
    public static final String PROJECT_UPDATE = "project:update";
    public static final String PROJECT_DELETE = "project:delete";
    public static final String PROJECT_MANAGE_MEMBERS = "project:manage_members";

    /**
     * The read half of the scope layer for projects.
     *
     * <p>The requirements say an employee views <em>assigned</em> projects. Without this grant a
     * listing returns only the projects the caller belongs to, owns, or whose team they lead; with it,
     * every project in the workspace. It is applied in the query rather than at the method boundary,
     * because it decides which rows come back rather than whether the call is allowed at all.
     */
    public static final String PROJECT_READ_ANY = "project:read_any";

    /** The write half, widening {@link #PROJECT_UPDATE} and {@link #PROJECT_MANAGE_MEMBERS} to every project. */
    public static final String PROJECT_MANAGE_ANY = "project:manage_any";

    public static final String TASK_READ = "task:read";
    public static final String TASK_CREATE = "task:create";
    public static final String TASK_UPDATE = "task:update";
    public static final String TASK_ASSIGN = "task:assign";
    public static final String TASK_CHANGE_STATUS = "task:change_status";
    public static final String TASK_DELETE = "task:delete";

    /**
     * The write half of the scope layer for tasks.
     *
     * <p>Without it, a caller reaches a task only as its assignee, as its reporter, as the owner of
     * its project, or as the lead of that project's team. With it, every task in the workspace.
     *
     * <p>There is deliberately no {@code task:read_any} beside it. Task read scope <em>is</em>
     * project read scope, so {@link #PROJECT_READ_ANY} already widens it, and a second read grant
     * would be a parallel model with its own resolution path that no test of the first one covers.
     */
    public static final String TASK_MANAGE_ANY = "task:manage_any";

    public static final String COMMENT_CREATE = "comment:create";

    /**
     * Editing a comment, which is author-only however wide the caller's other grants are.
     *
     * <p>{@link #COMMENT_MANAGE_ANY} does not widen this one. An administrator may remove somebody's
     * words; nobody may rewrite them and leave them attributed to the person who wrote them.
     */
    public static final String COMMENT_UPDATE = "comment:update";

    public static final String COMMENT_DELETE = "comment:delete";

    /**
     * The write half of the scope layer for comments.
     *
     * <p>Without it, a caller may remove a comment they wrote, or one on a task in a project they own
     * or lead the team of. With it, any comment in the workspace. It widens deletion only, for the
     * reason given on {@link #COMMENT_UPDATE}.
     *
     * <p>There is deliberately no {@code comment:read} beside it. A comment is visible exactly when
     * its task is, so {@link #TASK_READ} is the gate, and a second read grant would be a parallel
     * model with its own resolution path that no test of the first one covers.
     */
    public static final String COMMENT_MANAGE_ANY = "comment:manage_any";

    public static final String ATTACHMENT_CREATE = "attachment:create";
    public static final String ATTACHMENT_DELETE = "attachment:delete";

    /** The same widening for files, and with no {@code attachment:read} beside it either. */
    public static final String ATTACHMENT_MANAGE_ANY = "attachment:manage_any";

    /**
     * Browsing the workspace-wide audit history.
     *
     * <p>One record's own history needs nothing but the ability to see that record, so a task's
     * activity is gated by {@link #TASK_READ}. This code is for the listing that crosses every
     * project in the workspace, which is administration rather than collaboration.
     */
    public static final String ACTIVITY_READ = "activity:read";

    /** The catalog as data, for the test that compares it against the seeded rows. */
    public static final Set<String> ALL = Set.of(
            USER_READ,
            USER_UPDATE,
            USER_ACTIVATE,
            USER_DEACTIVATE,
            USER_DELETE,
            WORKSPACE_READ,
            WORKSPACE_CREATE,
            WORKSPACE_UPDATE,
            WORKSPACE_DELETE,
            WORKSPACE_ARCHIVE,
            MEMBER_READ,
            MEMBER_INVITE,
            MEMBER_REMOVE,
            MEMBER_ASSIGN_ROLE,
            ROLE_READ,
            ROLE_MANAGE,
            PERMISSION_READ,
            TEAM_READ,
            TEAM_CREATE,
            TEAM_UPDATE,
            TEAM_DELETE,
            TEAM_MANAGE_MEMBERS,
            TEAM_MANAGE_ANY,
            PROJECT_READ,
            PROJECT_READ_ANY,
            PROJECT_CREATE,
            PROJECT_UPDATE,
            PROJECT_DELETE,
            PROJECT_MANAGE_MEMBERS,
            PROJECT_MANAGE_ANY,
            TASK_READ,
            TASK_CREATE,
            TASK_UPDATE,
            TASK_ASSIGN,
            TASK_CHANGE_STATUS,
            TASK_DELETE,
            TASK_MANAGE_ANY,
            COMMENT_CREATE,
            COMMENT_UPDATE,
            COMMENT_DELETE,
            COMMENT_MANAGE_ANY,
            ATTACHMENT_CREATE,
            ATTACHMENT_DELETE,
            ATTACHMENT_MANAGE_ANY,
            ACTIVITY_READ);

    private Permissions() {}
}
