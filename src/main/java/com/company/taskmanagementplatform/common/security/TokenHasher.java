package com.company.taskmanagementplatform.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Generates and hashes the opaque tokens: refresh, email verification, password reset, invitation.
 *
 * <p>SHA-256 rather than a password hash, and that is deliberate. These values are 256 bits of
 * output from a cryptographic random source, so there is no dictionary to slow an attacker down and
 * no work factor worth paying. A refresh happens on every session, and making it cost a bcrypt
 * comparison would buy nothing at all.
 *
 * <p>The raw value is returned once, to be sent to its recipient. Only the hash is ever stored, so a
 * copy of the database yields no usable token.
 */
@Component
public class TokenHasher {

    /** 256 bits. Long enough that guessing is not a threat model worth discussing. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** A fresh token, URL-safe and unpadded so it survives a query string untouched. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java implementation, so this cannot happen.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Compares in constant time. Lookups are by hash, so this is belt and braces rather than the
     * primary defence, but a short-circuiting comparison on a secret is never worth keeping.
     */
    public boolean matches(String rawToken, String expectedHash) {
        if (rawToken == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawToken).getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
