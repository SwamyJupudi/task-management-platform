package com.company.taskmanagementplatform.users;

/**
 * Where an account is in its life.
 *
 * <p>A temporary lockout after failed sign-ins is not here. It lives in its own columns, because a
 * lock is a short machine decision and a deactivation is a durable human one, and a single column
 * holding both would make each harder to read.
 *
 * <p>Removal is not here either. It is the {@code deleted_at} timestamp, and it means the account is
 * gone rather than suspended.
 */
public enum UserStatus {

    /**
     * Registered and waiting for an administrator.
     *
     * <p>Can sign in, and that is deliberate: somebody who has just registered should be able to see
     * that the platform knows who they are and is waiting on somebody else, rather than being told
     * their password is wrong. They reach their own account and nothing else -- every workspace,
     * project and task is scoped to a membership they do not have yet.
     */
    PENDING_APPROVAL,

    /** Approved by an administrator, and a member of at least one workspace. */
    ACTIVE,

    /** Switched off by an administrator. Existing sessions stop at the next request. */
    DEACTIVATED
}
