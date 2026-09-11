package com.company.taskmanagementplatform.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Rewrites the words of a comment, and nothing else.
 *
 * <p>The body is required rather than optional. An edit that changes nothing is not an edit, and a
 * null body here would have to mean "leave it alone", which is what not calling this does.
 *
 * <p>Editing is author-only, however wide the caller's grants are. An administrator may remove
 * somebody's words; nobody may rewrite them and leave them attributed to their author.
 */
@Schema(name = "UpdateCommentRequest")
public record UpdateCommentRequest(
        @NotBlank @Size(max = 5000) @Schema(example = "Handing this back, the tests still fail") String body) {}
