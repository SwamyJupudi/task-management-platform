package com.company.taskmanagementplatform.comments;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the people named in a comment.
 *
 * <p>The canonical form is {@code @[user:<uuid>]}, written into the body by whatever composed it and
 * read back out by the server. Two decisions are worth stating, because both are the reason this
 * class exists rather than a field on the request.
 *
 * <p><strong>The server parses; the client does not send a list.</strong> A supplied list is a
 * second statement of the same fact, and the two disagree the moment somebody edits the text and not
 * the list. That produces either a notification for a name that is no longer in the comment or
 * silence for one that is, and neither failure is visible to the person who caused it.
 *
 * <p><strong>No display name is stored in the body.</strong> A name baked into the text is a copy of
 * the user table that goes stale the day somebody is renamed. The identifier is stored, and the
 * response carries the current name beside it.
 *
 * <p>Order is preserved and duplicates are collapsed: naming somebody twice in one comment is one
 * mention, which is also what the primary key on {@code comment_mentions} says.
 */
final class MentionParser {

    /**
     * Deliberately strict about the shape of the identifier.
     *
     * <p>A looser pattern would let {@code @[user:whatever]} through to be rejected later as a
     * malformed UUID, turning a typo in prose into a failed request. Anything that is not exactly
     * this is ordinary text and is left alone.
     */
    private static final Pattern MENTION =
            Pattern.compile("@\\[user:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})]");

    private MentionParser() {}

    /** The people named in this body, in the order they first appear. */
    static Set<UUID> parse(String body) {
        if (body == null || body.isEmpty()) {
            return Set.of();
        }

        Set<UUID> mentioned = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(body);
        while (matcher.find()) {
            // The pattern already proved the shape, so this cannot throw.
            mentioned.add(UUID.fromString(matcher.group(1)));
        }
        return mentioned;
    }
}
