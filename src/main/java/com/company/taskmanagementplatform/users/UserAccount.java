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
        Instant createdAt) {

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
