package com.company.taskmanagementplatform.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/** What a comment may contain, and what it may not. */
class CommentBodyTest {

    /** Built rather than escaped, so no control character is ever a byte in this file. */
    private static final String NUL = String.valueOf((char) 0);

    private static final String BELL = String.valueOf((char) 7);

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(CommentBody.require("  looks fine to me  ")).isEqualTo("looks fine to me");
    }

    @Test
    void refusesAnEmptyComment() {
        assertThatThrownBy(() -> CommentBody.require("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("needs something in it");
    }

    @Test
    void refusesAnAbsentBody() {
        assertThatThrownBy(() -> CommentBody.require(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptsOneExactlyAtTheLimit() {
        String body = "x".repeat(CommentBody.MAX_LENGTH);

        assertThat(CommentBody.require(body)).hasSize(CommentBody.MAX_LENGTH);
    }

    @Test
    void refusesOneOverTheLimit() {
        String body = "x".repeat(CommentBody.MAX_LENGTH + 1);

        assertThatThrownBy(() -> CommentBody.require(body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void keepsTheThreeControlCharactersThatBelongInProse() {
        assertThat(CommentBody.require("first\nsecond\r\n\tindented")).contains("\n", "\t");
    }

    @Test
    void refusesOtherControlCharactersRatherThanStrippingThem() {
        // Silently removing part of what somebody wrote is worse than telling them
        // it cannot be stored.
        assertThatThrownBy(() -> CommentBody.require("looks" + NUL + "fine"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be stored");

        assertThatThrownBy(() -> CommentBody.require("a bell" + BELL + " here"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void storesMarkupAsTheTextItIs() {
        // Nothing is escaped or stripped here. The body is text, escaping belongs
        // to whatever renders it, and rewriting somebody's words would be worse.
        String body = "use <script>alert(1)</script> in the example";

        assertThat(CommentBody.require(body)).isEqualTo(body);
    }
}
