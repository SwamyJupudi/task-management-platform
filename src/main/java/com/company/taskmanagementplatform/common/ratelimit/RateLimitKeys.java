package com.company.taskmanagementplatform.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.company.taskmanagementplatform.common.util.Emails;

/**
 * Builds the keys the limiter counts against.
 *
 * <p>One place, because a key built two ways is two counters and the limit silently doubles.
 *
 * <p><strong>An email address is never a key, only its hash is.</strong> Redis keys are readable by
 * anybody with access to the store, they show up in {@code MONITOR} output, in slow-log entries and in
 * whatever a managed provider's dashboard chooses to display, and none of those are places the platform
 * puts customer addresses. A SHA-256 of the normalised address gives a stable key with the same
 * collision properties and none of the disclosure. The digest is not a secret and is not treated as one;
 * it exists so that the store holds no personal data.
 *
 * <p>The address is normalised through {@link Emails} first, for the reason that class exists: without
 * it, {@code Ada@example.com} and {@code ada@example.com} would be two counters for one account, and
 * the per-account limit would be trivially doubled by varying the capitalisation.
 *
 * <p>Every key carries a version prefix. Changing the shape of a key without it would leave the old
 * counters in the store under keys nothing reads, holding their windows open until they expired; with
 * it, a change is a clean break.
 */
public final class RateLimitKeys {

    /** Bumped only if the meaning of a key changes, which resets every counter. */
    private static final String PREFIX = "rl:v1";

    private RateLimitKeys() {}

    /**
     * The caller's network address.
     *
     * @param address as the container reports it, which behind a proxy means the one {@code
     *     server.forward-headers-strategy} resolved rather than a header any client could set
     */
    public static String forAddress(String rule, String address) {
        String subject = address == null || address.isBlank() ? "unknown" : address;
        return PREFIX + ":" + rule + ":ip:" + subject;
    }

    /** One account, identified by the hash of its address rather than by the address. */
    public static String forEmail(String rule, String email) {
        return PREFIX + ":" + rule + ":em:" + hash(Emails.normalize(email));
    }

    private static String hash(String value) {
        String subject = value == null ? "" : value.toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
