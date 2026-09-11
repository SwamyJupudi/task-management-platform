package com.company.taskmanagementplatform.comments.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Adds a comment to a task.
 *
 * <p>There is no field for the people mentioned. They are read out of the body by the server, which
 * is the only way the text and the notifications cannot disagree; see {@code MentionParser}.
 *
 * <p>{@code attachmentIds} names files already uploaded to this task by this caller and not yet
 * attached to any comment. Files arrive through the upload endpoint first, because mixing a file
 * part and a JSON part in one request is awkward for every client and makes a partial failure
 * ambiguous.
 */
@Schema(name = "CreateCommentRequest")
public record CreateCommentRequest(
        @NotBlank
                @Size(max = 5000)
                @Schema(
                        description = "Plain text. Mention somebody by including @[user:<uuid>]",
                        example = "Handing this to @[user:3f0b4f7e-6a1e-4c62-9a51-9a5e0a5a1b2c] for review")
                String body,
        @Size(max = 10)
                @Schema(description = "Files already uploaded to this task by you, to attach to this comment")
                List<UUID> attachmentIds) {}
