package com.company.taskmanagementplatform.comments.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One comment, with its author and everybody it names resolved.
 *
 * <p>The mentions are returned rather than left for the client to parse a second time, and they
 * carry the current name and address. Nothing about a person is stored in the body, so a rename is
 * reflected here the next time the comment is read instead of leaving a stale name in the text.
 *
 * <p>Attachments are deliberately absent. A file carries the comment it belongs to, so the task's
 * attachment listing groups by comment without this module having to know anything about files.
 */
@Schema(name = "Comment")
public record CommentResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID taskId,
        UUID authorUserId,
        @Schema(example = "ada@example.com") String authorEmail,
        @Schema(example = "Ada Lovelace") String authorName,
        @Schema(description = "Plain text, never HTML. Escape it before rendering") String body,
        @Schema(description = "True when the author has changed it since writing it") boolean edited,
        @Schema(description = "When the words last changed, or null") Instant editedAt,
        @Schema(description = "Everybody named in the body") List<MentionResponse> mentions,
        Instant createdAt,
        Instant updatedAt) {

    /** One person named in a comment, resolved so the client renders a name rather than a uuid. */
    @Schema(name = "Mention")
    public record MentionResponse(
            UUID userId,
            @Schema(example = "ada@example.com") String email,
            @Schema(example = "Ada Lovelace") String name) {}
}
