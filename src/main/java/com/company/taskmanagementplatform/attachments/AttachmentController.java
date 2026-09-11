package com.company.taskmanagementplatform.attachments;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.tasks.TaskAccessGuard;
import com.company.taskmanagementplatform.tasks.TaskRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Files held against a task.
 *
 * <p>Uploaded and listed under the task, addressed flat afterwards, for the reason comments are: a
 * link should not have to carry the task to reach the file it points at.
 *
 * <p>A file is uploaded to the task first and claimed by a comment afterwards, if it belongs to one.
 * That keeps this endpoint a plain multipart upload with no JSON part beside it.
 *
 * <p>Downloads always leave as an attachment, never inline, and always with {@code nosniff}. Between
 * them those two headers are what stop a file the browser might have rendered from being rendered:
 * the allowlist in {@code ContentTypes} is the first lock and this is the second.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}")
@Tag(name = "Attachments", description = "Files on a task or on a comment")
class AttachmentController {

    private final AttachmentService attachments;
    private final TaskAccessGuard guard;

    AttachmentController(AttachmentService attachments, TaskAccessGuard guard) {
        this.attachments = attachments;
        this.guard = guard;
    }

    @GetMapping("/tasks/{taskId}/attachments")
    @Operation(summary = "List a task's files", description = "Includes the files on its comments")
    List<AttachmentResponse> list(@PathVariable UUID workspaceId, @PathVariable UUID taskId) {
        return attachments.list(guard.requireReadableTask(workspaceId, taskId));
    }

    /**
     * Uploads one file.
     *
     * <p>Anybody who can see the task may do this, exactly as anybody who can see it may comment.
     */
    @PostMapping(path = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Attach a file to a task",
            description = "The type is detected from the file's own bytes; the claimed type is ignored")
    AttachmentResponse upload(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @RequestParam("file") MultipartFile file) {

        TaskRef task = guard.requireContributableTask(workspaceId, taskId, Permissions.ATTACHMENT_CREATE);
        return attachments.upload(task, file, CurrentUser.requireId());
    }

    @GetMapping("/attachments/{attachmentId}")
    @Operation(summary = "One file's details", description = "Without the storage key, which is internal")
    AttachmentResponse metadata(@PathVariable UUID workspaceId, @PathVariable UUID attachmentId) {
        return attachments.metadata(workspaceId, attachmentId);
    }

    /**
     * Downloads the bytes.
     *
     * <p>Answers 302 to a short-lived signed URL when the storage provider can issue one, and streams
     * otherwise. The local store cannot, so today this always streams, which means the streaming path
     * is exercised rather than theoretical.
     */
    @GetMapping("/attachments/{attachmentId}/content")
    @Operation(summary = "Download a file", description = "Always as an attachment, never rendered inline")
    ResponseEntity<?> download(@PathVariable UUID workspaceId, @PathVariable UUID attachmentId) {
        AttachmentContent content = attachments.download(workspaceId, attachmentId);

        if (content.isRedirect()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(content.redirectTo()).build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(content.filename()))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .body(new InputStreamResource(content.stream()));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @Operation(summary = "Remove a file", description = "Soft delete; the stored bytes await the purge")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID attachmentId) {
        attachments.delete(workspaceId, attachmentId, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Builds the header Spring's own type encodes correctly.
     *
     * <p>A filename may hold anything the sanitiser allowed, including non-ASCII, and hand-writing
     * this header is how a name with a quote or an umlaut in it either breaks the response or becomes
     * a way to add a second header.
     */
    private static String disposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
