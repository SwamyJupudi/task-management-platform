package com.company.taskmanagementplatform.common.util;

import java.util.Locale;

/**
 * Normalises an email address to the single form the application stores and queries.
 *
 * <p>This is not cosmetic, and skipping it would introduce a quiet correctness bug. The columns are
 * {@code citext}, so the unique index is case-insensitive, but a query parameter arrives from the
 * driver typed as {@code varchar}. PostgreSQL then has no {@code citext = varchar} operator and
 * resolves the comparison by casting the column down to {@code varchar} instead, which compares case
 * sensitively. The index and the lookup would disagree: registering an address twice in different
 * cases would be refused, while signing in with the wrong case would report no such account.
 *
 * <p>Normalising on the way in makes both sides agree regardless of how the operator resolves, and
 * leaves {@code citext} as a second line of defence rather than the only one.
 *
 * <p>Only case and surrounding whitespace are touched. The local part is not otherwise rewritten:
 * stripping dots or plus-addressing would merge addresses that some providers treat as distinct.
 */
public final class Emails {

    private Emails() {}

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
