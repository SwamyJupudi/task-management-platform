package com.company.taskmanagementplatform.comments;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.comments.dto.CreateCommentRequest;
import com.company.taskmanagementplatform.comments.dto.UpdateCommentRequest;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.tasks.TaskAccessGuard;
import com.company.taskmanagementplatform.tasks.TaskRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The discussion on a task.
 *
 * <p>Written and read under the task, because a comment cannot exist without one. Addressed flat
 * afterwards, exactly as a task is: a link from a notification should not have to carry the task to
 * reach the comment it is about.
 *
 * <p>Reading a thread needs nothing but {@code task:read}. There is deliberately no {@code
 * comment:read}: a comment is visible exactly when its task is, and a second read grant would be a
 * parallel model with its own resolution path that no test of the first one covers.
 *
 * <p>The two flat endpoints authorize inside {@link CommentService} rather than here, because which
 * task a comment belongs to is not known until it has been loaded. Everything else follows the
 * platform convention and is authorized before the service is called.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}")
@Tag(name = "Comments", description = "Discussion on a task, with mentions")
class CommentController {

    private final CommentService comments;
    private final TaskAccessGuard guard;

    CommentController(CommentService comments, TaskAccessGuard guard) {
        this.comments = comments;
        this.guard = guard;
    }

    @GetMapping("/tasks/{taskId}/comments")
    @Operation(summary = "Read a task's comments", description = "Oldest first, paged")
    PageResponse<CommentResponse> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @PageableDefault(size = 20) Pageable pageable) {

        TaskRef task = guard.requireReadableTask(workspaceId, taskId);
        return comments.list(task, pageable);
    }

    /**
     * Comments on a task.
     *
     * <p>Anybody who can see the task may do this. The write scope that narrows editing a task to its
     * assignee, reporter, project owner or team lead is deliberately not applied: an employee on a
     * project who holds none of those roles can still take part in the discussion, which is the whole
     * point of one.
     */
    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Comment on a task",
            description = "Mentions are read out of the body as @[user:<uuid>]")
    CommentResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateCommentRequest request) {

        TaskRef task = guard.requireContributableTask(workspaceId, taskId, Permissions.COMMENT_CREATE);
        return comments.create(task, request, CurrentUser.requireId());
    }

    /** Author-only, whatever else the caller holds. */
    @PatchMapping("/comments/{commentId}")
    @Operation(summary = "Edit your own comment", description = "Records when the words changed")
    CommentResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {

        return comments.update(workspaceId, commentId, request, CurrentUser.requireId());
    }

    /** The author, the project owner, its team lead, or a holder of {@code comment:manage_any}. */
    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Remove a comment", description = "Soft delete; its attachments go too")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID commentId) {
        comments.delete(workspaceId, commentId, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }
}
