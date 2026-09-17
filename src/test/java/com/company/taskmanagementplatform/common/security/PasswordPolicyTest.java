package com.company.taskmanagementplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.common.error.BadRequestException;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(properties(8, 72));

    @Test
    void acceptsAPasswordAtTheMinimumLength() {
        assertThatCode(() -> policy.validate("12345678")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAPasswordBelowTheMinimumLength() {
        assertThatThrownBy(() -> policy.validate("1234567"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> policy.validate("")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void imposesNoCompositionRules() {
        // Lower case only, no digits, no symbols. The requirements ask for no such
        // rules and this test is here so nobody adds one without a reason.
        assertThatCode(() -> policy.validate("correcthorsebatterystaple")).doesNotThrowAnyException();
    }

    @Test
    void acceptsExactlySeventyTwoBytes() {
        assertThatCode(() -> policy.validate("a".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreThanSeventyTwoBytes() {
        // The bound that matters: BCrypt ignores anything past here, so without the
        // check two different passwords would open the same account.
        assertThatThrownBy(() -> policy.validate("a".repeat(73)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void countsBytesRatherThanCharacters() {
        // Nineteen emoji are nineteen characters and seventy-six bytes. Counting
        // characters would let this through and BCrypt would silently truncate it.
        String emoji = "🔐".repeat(19);
        assertThat(emoji.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

        assertThatThrownBy(() -> policy.validate(emoji)).isInstanceOf(BadRequestException.class);
    }

    private static SecurityProperties properties(int minLength, int maxBytes) {
        return new SecurityProperties(
                new SecurityProperties.Jwt("issuer", "audience", "x".repeat(32), Duration.ofMinutes(15)),
                new SecurityProperties.Cookie("refresh_token", "/api/v1/auth", true, Duration.ofDays(14)),
                new SecurityProperties.Password(minLength, maxBytes),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new SecurityProperties.Tokens(Duration.ofHours(24), Duration.ofHours(1)));
    }
}
