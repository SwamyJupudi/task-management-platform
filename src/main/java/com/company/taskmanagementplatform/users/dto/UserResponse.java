package com.company.taskmanagementplatform.users.dto;

import java.time.Instant;
import java.util.UUID;

import com.company.taskmanagementplatform.users.UserAccount;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How an account is described to a client.
 *
 * <p>Assembled from {@link UserAccount}, which already excludes the password hash, so there is no
 * path by which a hash could reach a response even by mistake.
 */
@Schema(name = "User", description = "A user account")
public record UserResponse(
        @Schema(description = "Identifier") UUID id,
        @Schema(description = "Email address", example = "person@example.com") String email,
        @Schema(example = "Ada") String firstName,
        @Schema(example = "Lovelace") String lastName,
        @Schema(description = "Lifecycle state", example = "ACTIVE") String status,
        @Schema(description = "Whether the address has been confirmed") boolean emailVerified,
        @Schema(description = "When the account last signed in") Instant lastLoginAt,
        @Schema(description = "When the account was created") Instant createdAt) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.id(),
                account.email(),
                account.firstName(),
                account.lastName(),
                account.status().name(),
                account.isEmailVerified(),
                account.lastLoginAt(),
                account.createdAt());
    }
}
