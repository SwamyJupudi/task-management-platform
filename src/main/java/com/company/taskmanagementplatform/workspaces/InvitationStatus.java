package com.company.taskmanagementplatform.workspaces;

/**
 * Where an invitation stands.
 *
 * <p>Expiry is recorded as well as computed. A row whose moment has passed is treated as expired the
 * instant it is read, without waiting for anything to sweep it, and the status is settled when it is
 * next touched. Nothing depends on a scheduler having run.
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED
}
