package com.company.taskmanagementplatform.users;

import java.util.UUID;

/**
 * What this module announces when an administrator acts on somebody's account.
 *
 * <p>Separate from {@link UserDeactivatedEvent} and {@link UserDeletedEvent}, which already exist
 * and are consumed <em>inside</em> the writing transaction by {@code auth} to revoke sessions and by
 * {@code workspaces} to remove memberships. These are consumed after commit by {@code activity} to
 * write an audit row, and that is a different need with a different timing. Widening the existing
 * pair to serve both would grow them fields their first consumers never read, which is how an event
 * turns into a shared mutable type.
 *
 * <p>Every record carries the facts the audit row needs, resolved before publication. That ordering
 * is load-bearing for {@link Deleted}: by the time the audit listener runs, the in-transaction
 * listeners have already removed the person's memberships, so anything the row wanted from them has
 * to have been read first.
 *
 * <p>The actor is nullable throughout. An account created or changed by the startup bootstrap has no
 * person behind it, and inventing one would put a fictional actor in the audit trail. {@code
 * ActivitySummaries} already renders a null actor as "The platform".
 */
public final class UserAdminEvents {

    private UserAdminEvents() {}

    /** An administrator edited somebody else's name. Never published for a self-service edit. */
    public record ProfileUpdated(UUID actorUserId, UUID userId, String firstName, String lastName) {}

    /** The account was switched back on. The status is carried because reactivation may land on
     * {@code PENDING_VERIFICATION} rather than {@code ACTIVE}. */
    public record Activated(UUID actorUserId, UUID userId, UserStatus status) {}

    public record Deactivated(UUID actorUserId, UUID userId) {}

    /**
     * The account was soft-deleted.
     *
     * <p>The address is carried rather than looked up later, because the row is now hidden from
     * every ordinary read and an audit entry naming only an identifier would be unreadable.
     */
    public record Deleted(UUID actorUserId, UUID userId, String email) {}

    /** A lockout was cleared by hand. Only published when there was one to clear. */
    /** An administrator let a waiting account in. The workspace it was given is audited beside it. */
    public record Approved(UUID actorUserId, UUID userId) {}

    public record Unlocked(UUID actorUserId, UUID userId) {}

    /** An administrator started a password recovery. The token itself is never in an event. */
    public record PasswordResetRequested(UUID actorUserId, UUID userId) {}

    public record VerificationResent(UUID actorUserId, UUID userId) {}
}
