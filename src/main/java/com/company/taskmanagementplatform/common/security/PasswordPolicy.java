package com.company.taskmanagementplatform.common.security;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The rules a new password has to satisfy.
 *
 * <p>A length floor and a length ceiling, and nothing else. Composition rules are absent on purpose:
 * the requirements document asks for none, and in practice they push people towards predictable
 * substitutions rather than better secrets.
 *
 * <p>The ceiling is not a style choice. BCrypt hashes at most the first 72 bytes of its input and
 * silently ignores the rest, so without a limit two different long passwords could open the same
 * account. The limit is counted in bytes rather than characters, because that is what BCrypt counts
 * and a single emoji can occupy four of them.
 */
@Component
public class PasswordPolicy {

    private final SecurityProperties.Password properties;

    public PasswordPolicy(SecurityProperties properties) {
        this.properties = properties.password();
    }

    /** @throws BadRequestException with a message the user can act on */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new BadRequestException("A password is required.");
        }
        if (rawPassword.length() < properties.minLength()) {
            throw new BadRequestException(
                    "The password must be at least " + properties.minLength() + " characters long.");
        }
        int bytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > properties.maxBytes()) {
            throw new BadRequestException("The password is too long. The limit is " + properties.maxBytes()
                    + " bytes, and this one is " + bytes + ".");
        }
    }

    public int minLength() {
        return properties.minLength();
    }

    public int maxBytes() {
        return properties.maxBytes();
    }
}
