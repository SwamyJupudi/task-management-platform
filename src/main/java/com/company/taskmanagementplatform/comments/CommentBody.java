package com.company.taskmanagementplatform.comments;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * What a comment is allowed to contain.
 *
 * <p>Text, never markup. Escaping is the job of whatever renders it, and storing HTML would make
 * this table the place an injected script lives, waiting for the one screen that forgets to escape.
 *
 * <p>Control characters are refused rather than stripped. Silently removing part of what somebody
 * wrote is worse than telling them it cannot be stored, and the three that do belong in prose are
 * allowed through.
 */
final class CommentBody {

    static final int MAX_LENGTH = 5000;

    private CommentBody() {}

    static String require(String raw) {
        String body = raw == null ? "" : raw.trim();

        if (body.isEmpty()) {
            throw new BadRequestException("A comment needs something in it.");
        }
        if (body.length() > MAX_LENGTH) {
            throw new BadRequestException("A comment may be at most " + MAX_LENGTH + " characters.");
        }
        if (hasControlCharacters(body)) {
            throw new BadRequestException("That comment contains characters that cannot be stored.");
        }
        return body;
    }

    private static boolean hasControlCharacters(String body) {
        return body.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t');
    }
}
