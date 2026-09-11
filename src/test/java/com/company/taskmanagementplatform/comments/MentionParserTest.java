package com.company.taskmanagementplatform.comments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The rules for finding people in a comment.
 *
 * <p>Lives in the same package as the parser because the parser is package-private, which is
 * deliberate: nothing outside this module has any business reading a mention out of text.
 */
class MentionParserTest {

    private static final UUID ADA = UUID.fromString("3f0b4f7e-6a1e-4c62-9a51-9a5e0a5a1b2c");
    private static final UUID GRACE = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    void findsOneMentionInOrdinaryProse() {
        assertThat(MentionParser.parse("handing this to @[user:" + ADA + "] for review"))
                .containsExactly(ADA);
    }

    @Test
    void findsSeveralInTheOrderTheyAppear() {
        String body = "@[user:" + GRACE + "] and @[user:" + ADA + "] should both look";

        assertThat(MentionParser.parse(body)).containsExactly(GRACE, ADA);
    }

    @Test
    void namingSomebodyTwiceIsOneMention() {
        // The same rule the primary key on comment_mentions states.
        String body = "@[user:" + ADA + "] ... and again @[user:" + ADA + "]";

        assertThat(MentionParser.parse(body)).containsExactly(ADA);
    }

    @Test
    void ignoresTextThatMerelyLooksLikeAMention() {
        // A typo in prose must not become a failed request, which is why the
        // pattern insists on the exact shape of a uuid rather than accepting
        // anything and rejecting it later.
        assertThat(MentionParser.parse("email @ada, see @[user:not-a-uuid] and @[user:]"))
                .isEmpty();
    }

    @Test
    void acceptsAnIdentifierInEitherCase() {
        String body = "@[user:" + ADA.toString().toUpperCase(java.util.Locale.ROOT) + "]";

        assertThat(MentionParser.parse(body)).containsExactly(ADA);
    }

    @Test
    void findsAMentionWithNoSpaceAroundIt() {
        assertThat(MentionParser.parse("(@[user:" + ADA + "])")).containsExactly(ADA);
    }

    @Test
    void handlesAbsentAndEmptyBodies() {
        assertThat(MentionParser.parse(null)).isEmpty();
        assertThat(MentionParser.parse("")).isEmpty();
        assertThat(MentionParser.parse("nobody at all")).isEmpty();
    }

    @Test
    void returnsThemAsAList() {
        assertThat(List.copyOf(MentionParser.parse("@[user:" + ADA + "]"))).hasSize(1);
    }
}
