package com.company.taskmanagementplatform.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * What goes into a key, and the one thing that must never: an email address.
 *
 * <p>Keys are the part of this feature that is visible outside the application. They appear in {@code
 * MONITOR} output, in slow-log entries and in whatever a managed Redis provider puts on a dashboard, and
 * none of those are places this platform writes a customer's address.
 */
class RateLimitKeysTest {

    @Test
    void anAddressKeyNamesTheRuleAndTheAddress() {
        assertThat(RateLimitKeys.forAddress("auth", "203.0.113.7")).isEqualTo("rl:v1:auth:ip:203.0.113.7");
    }

    @Test
    void anUnknownAddressStillProducesAUsableKey() {
        // getRemoteAddr can be null in a container that has not resolved a peer.
        // A null in the key would become the literal text "null" for every such
        // caller, which is the same counter; naming it is at least honest.
        assertThat(RateLimitKeys.forAddress("global", null)).isEqualTo("rl:v1:global:ip:unknown");
        assertThat(RateLimitKeys.forAddress("global", "  ")).isEqualTo("rl:v1:global:ip:unknown");
    }

    @Test
    void anAccountKeyNeverContainsTheAddress() {
        String key = RateLimitKeys.forEmail("login-email", "Ada.Lovelace@example.com");

        assertThat(key).doesNotContain("Ada");
        assertThat(key).doesNotContain("ada");
        assertThat(key).doesNotContain("example.com");
        assertThat(key).doesNotContain("@");
    }

    @Test
    void anAccountKeyIsAHexDigestOfAFixedLength() {
        String key = RateLimitKeys.forEmail("login-email", "ada@example.com");

        assertThat(key).startsWith("rl:v1:login-email:em:");
        String digest = key.substring("rl:v1:login-email:em:".length());
        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void capitalisationAndWhitespaceCannotBuyASecondAllowance() {
        // Without normalisation, Ada@example.com and ada@example.com would be two
        // counters for one account and the per-account limit would double for
        // anybody who noticed.
        String canonical = RateLimitKeys.forEmail("login-email", "ada@example.com");

        assertThat(RateLimitKeys.forEmail("login-email", "ADA@EXAMPLE.COM")).isEqualTo(canonical);
        assertThat(RateLimitKeys.forEmail("login-email", "  Ada@Example.com  ")).isEqualTo(canonical);
    }

    @Test
    void differentAccountsGetDifferentKeys() {
        assertThat(RateLimitKeys.forEmail("login-email", "ada@example.com"))
                .isNotEqualTo(RateLimitKeys.forEmail("login-email", "grace@example.com"));
    }

    @Test
    void differentRulesCountSeparatelyForTheSameSubject() {
        // Otherwise a registration attempt would consume a sign-in allowance.
        assertThat(RateLimitKeys.forEmail("login-email", "ada@example.com"))
                .isNotEqualTo(RateLimitKeys.forEmail("register-email", "ada@example.com"));
        assertThat(RateLimitKeys.forAddress("auth", "203.0.113.7"))
                .isNotEqualTo(RateLimitKeys.forAddress("global", "203.0.113.7"));
    }

    @Test
    void everyKeyCarriesTheVersionPrefix() {
        // Without it, changing the shape of a key would leave the old counters in
        // the store under keys nothing reads, holding their windows open.
        assertThat(RateLimitKeys.forAddress("global", "203.0.113.7")).startsWith("rl:v1:");
        assertThat(RateLimitKeys.forEmail("login-email", "ada@example.com")).startsWith("rl:v1:");
    }

    @Test
    void aNullAddressHashesRatherThanFailing() {
        // Reached only by a caller passing a null through; it must not be the thing
        // that turns a rate limit check into a 500.
        assertThat(RateLimitKeys.forEmail("login-email", null)).startsWith("rl:v1:login-email:em:");
    }
}
