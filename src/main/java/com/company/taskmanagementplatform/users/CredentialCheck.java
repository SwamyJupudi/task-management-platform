package com.company.taskmanagementplatform.users;

import java.util.UUID;

/**
 * The answer to "is this password right, and may this account sign in".
 *
 * <p>Both questions are answered here rather than in {@code auth}, because answering the first one
 * requires the password hash and the hash must not leave this module.
 *
 * <p>The order the outcomes are decided in matters. The password is checked first and every wrong
 * password produces {@link Outcome#INVALID_CREDENTIALS}, whatever else may be true of the account.
 * Only once the password is known to be correct does the outcome start describing the account, so a
 * caller who does not know the password cannot learn whether an address is registered, locked or
 * switched off.
 */
public record CredentialCheck(Outcome outcome, UUID userId) {

    public enum Outcome {
        /** Correct password, account may sign in. */
        SUCCESS,
        /** No such account, or the wrong password. The two are deliberately indistinguishable. */
        INVALID_CREDENTIALS,
        /** Correct password, but too many recent failures. */
        LOCKED,
        /** Correct password, but an administrator switched the account off. */
        INACTIVE
    }

    public static CredentialCheck invalid() {
        return new CredentialCheck(Outcome.INVALID_CREDENTIALS, null);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }
}
