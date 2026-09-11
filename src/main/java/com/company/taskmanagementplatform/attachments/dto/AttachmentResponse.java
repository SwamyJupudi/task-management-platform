package com.company.taskmanagementplatform.attachments.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One stored file, with its uploader resolved.
 *
 * <p>{@code commentId} is what lets a client group a task's files under the comments they belong to,
 * which is why a comment does not carry its attachments in its own body. Files uploaded to the task
 * itself, rather than to a comment, have none.
 *
 * <p>The storage key is deliberately absent. It is an internal address, it is the only thing
 * standing between a bucket listing and a file, and nothing a client does needs it. Downloads go
 * through {@code /attachments/{id}/content}, which authorizes first.
 */
@Schema(name = "Attachment")
public record AttachmentResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID taskId,
        @Schema(description = "The comment this file belongs to, or null if it hangs off the task")
                UUID commentId,
        UUID uploaderUserId,
        @Schema(example = "ada@example.com") String uploaderEmail,
        @Schema(example = "Ada Lovelace") String uploaderName,
        @Schema(example = "architecture.pdf") String filename,
        @Schema(description = "Detected from the file's own bytes, not from what the client claimed",
                        example = "application/pdf")
                String contentType,
        @Schema(example = "48213") long sizeBytes,
        @Schema(description = "Hex SHA-256 of the stored bytes") String checksumSha256,
        Instant createdAt) {}
