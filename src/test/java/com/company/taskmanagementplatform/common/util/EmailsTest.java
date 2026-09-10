package com.company.taskmanagementplatform.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailsTest {

    @Test
    void lowercasesSoLookupsMatchTheCaseInsensitiveIndex() {
        assertThat(Emails.normalize("Ada@Example.COM")).isEqualTo("ada@example.com");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(Emails.normalize("  ada@example.com  ")).isEqualTo("ada@example.com");
    }

    @Test
    void leavesTheLocalPartOtherwiseAlone() {
        // Stripping dots or plus-addressing would merge addresses that some
        // providers treat as different people.
        assertThat(Emails.normalize("ada.lovelace+work@example.com"))
                .isEqualTo("ada.lovelace+work@example.com");
    }

    @Test
    void passesNullThroughRatherThanThrowing() {
        assertThat(Emails.normalize(null)).isNull();
    }
}
