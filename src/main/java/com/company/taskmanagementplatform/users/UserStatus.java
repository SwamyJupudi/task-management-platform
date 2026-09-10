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

    /** Registered, not yet proved they own the address. Cannot sign in. */
    PENDING_VERIFICATION,

    /** Verified and permitted to sign in. */
    ACTIVE,

    /** Switched off by an administrator. Existing sessions stop at the next request. */
    DEACTIVATED
}
