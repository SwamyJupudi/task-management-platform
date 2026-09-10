package com.company.taskmanagementplatform.auth;

/**
 * The events that cause a message to be sent, each carrying the token that has just been generated.
 *
 * <p>The raw token travels on the event because it exists nowhere else: the table holds only its
 * hash. It is never logged and never stored.
 *
 * <p>Grouped in one file because they are one idea with three cases, and three files of a single line
 * each would say less than this does.
 */
final class IdentityMailEvents {

    private IdentityMailEvents() {}

    record VerificationRequested(String email, String rawToken) {}

    record PasswordResetRequested(String email, String rawToken) {}

    /** Carries no token: it tells somebody their password changed, so they can react if it was not them. */
    record PasswordChanged(String email) {}
}
