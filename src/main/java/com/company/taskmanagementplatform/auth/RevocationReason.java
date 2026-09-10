package com.company.taskmanagementplatform.auth;

/**
 * Why a refresh token stopped being usable.
 *
 * <p>Recorded rather than merely flagged, because the reasons are not equivalent. A chain of {@code
 * ROTATED} is a healthy session; a {@code REUSE_DETECTED} is a token that was presented twice, which
 * means two parties held it and one of them should not have. Keeping them apart is what makes that
 * visible afterwards.
 *
 * <p>The values match the check constraint on the table.
 */
enum RevocationReason {
    LOGOUT,
    LOGOUT_ALL,
    ROTATED,
    REUSE_DETECTED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    ACCOUNT_DEACTIVATED,
    SESSION_REVOKED
}
