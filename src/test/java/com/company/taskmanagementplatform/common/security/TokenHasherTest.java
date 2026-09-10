package com.company.taskmanagementplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @Test
    void generatesADifferentTokenEveryTime() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            generated.add(hasher.generate());
        }
        assertThat(generated).hasSize(500);
    }

    @Test
    void generatesUrlSafeTokensThatSurviveAQueryString() {
        for (int i = 0; i < 100; i++) {
            assertThat(hasher.generate()).matches("^[A-Za-z0-9_-]+$");
        }
    }

    @Test
    void generatesTokensWithAtLeast256BitsOfEntropy() {
        // 32 bytes, base64url without padding, is 43 characters.
        assertThat(hasher.generate()).hasSize(43);
    }

    @Test
    void hashesTheSameValueToTheSameDigest() {
        String token = hasher.generate();
        assertThat(hasher.hash(token)).isEqualTo(hasher.hash(token));
    }

    @Test
    void hashesDifferentValuesToDifferentDigests() {
        assertThat(hasher.hash("one")).isNotEqualTo(hasher.hash("two"));
    }

    @Test
    void neverStoresTheTokenItself() {
        String token = hasher.generate();
        assertThat(hasher.hash(token)).doesNotContain(token);
    }

    @Test
    void matchesATokenAgainstItsOwnDigest() {
        String token = hasher.generate();
        assertThat(hasher.matches(token, hasher.hash(token))).isTrue();
    }

    @Test
    void rejectsATokenAgainstAnotherDigest() {
        assertThat(hasher.matches(hasher.generate(), hasher.hash(hasher.generate()))).isFalse();
    }

    @Test
    void rejectsNullsRatherThanThrowing() {
        assertThat(hasher.matches(null, hasher.hash("x"))).isFalse();
        assertThat(hasher.matches("x", null)).isFalse();
    }
}
