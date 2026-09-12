package com.company.taskmanagementplatform.admin.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One account on the administrative directory.
 *
 * <p>The ordinary account listing at {@code GET /users} already exists and is not duplicated here.
 * This adds the two things the admin panel needs and that directory should not carry: how many
 * workspaces the person belongs to, and whether they hold the platform role.
 *
 * <p>No password hash, and no path by which one could arrive: this is assembled from {@code
 * UserAccount}, which does not have the field.
 *
 * @param workspaceCount how many workspaces this person belongs to, resolved for the whole page in
 *     one grouped query
 * @param platformAdministrator whether they hold the platform role. A boolean rather than the role
 *     identifier, because there is exactly one platform role and naming it would invite a client to
 *     believe there could be others
 * @param lockedUntil when an automatic lockout expires, or null. Distinct from {@code status}: a
 *     lock is a temporary machine decision and a deactivation a durable human one
 */
@Schema(name = "PlatformAccount", description = "An account, with its reach across the installation")
public record PlatformAccountResponse(
        UUID id,
        @Schema(example = "person@example.com") String email,
        String firstName,
        String lastName,
        @Schema(example = "ACTIVE") String status,
        boolean emailVerified,
        boolean platformAdministrator,
        long workspaceCount,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt) {}
