package com.company.taskmanagementplatform.users;

import java.time.Instant;
import java.util.UUID;

/**
 * What the rest of the application is allowed to know about an account.
 *
 * <p>The password hash is absent, and its absence is the point. Every module outside {@code users}
 * receives this record rather than the entity, so the hash is not a value that can be passed around,
 * put in a response by accident, or written to a log. Verifying a password is asked of {@link
 * UserAccountService} instead, which answers with an outcome.
 *
 * @param lockedUntil when an automatic lockout expires, or null. Carried beside {@code status}
 *     rather than folded into it, because a lock is a temporary decision made by the machine and a
 *     deactivation is a durable one made by a person. The admin panel needs to tell them apart: an
 *     account nobody switched off but that still cannot sign in is the case somebody is looking for.
 */
public record UserAccount(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Instant emailVerifiedAt,
        UUID platformRoleId,
        Instant lastLoginAt,
        Instant lockedUntil,
        Instant createdAt) {

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** Whether a lockout is still in force at this moment. An expired one grants nothing. */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
