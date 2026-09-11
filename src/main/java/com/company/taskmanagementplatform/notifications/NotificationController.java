package com.company.taskmanagementplatform.notifications;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.notifications.dto.NotificationResponse;
import com.company.taskmanagementplatform.workspaces.WorkspaceAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * One person's feed. Four ways to read or clear it, and no way to write one.
 *
 * <p><strong>No permission code gates any of these, and that is deliberate.</strong> Every other
 * module in the platform names a code at this point; this one names the recipient instead. A
 * notification has exactly one audience, so a {@code notification:read} grant would be held by
 * everybody and would gate nothing, which is the same reasoning that produced no {@code
 * comment:read} in phase six.
 *
 * <p>What is still enforced is membership. {@link WorkspaceAccessGuard#visiblePermissions} answers
 * 404 for a workspace the caller has nothing to do with, exactly as it does everywhere else, so a
 * stranger cannot learn that a workspace identifier is real by asking for its notifications. The
 * permission set it returns is then used for one thing only: deciding how much of the caller's own
 * feed still resolves to a task name.
 *
 * <p>Every method narrows to {@link CurrentUser}. There is no path, parameter or role that lets one
 * person read another's notifications.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/notifications")
@Tag(name = "Notifications", description = "What you have been told, and what you have read")
class NotificationController {

    private final NotificationService notifications;
    private final WorkspaceAccessGuard workspaceGuard;

    NotificationController(NotificationService notifications, WorkspaceAccessGuard workspaceGuard) {
        this.notifications = notifications;
        this.workspaceGuard = workspaceGuard;
    }

    @GetMapping
    @Operation(
            summary = "Your notifications in this workspace",
            description = "Newest first. Yours alone: there is no way to read anybody else's")
    PageResponse<NotificationResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean unread,
            @PageableDefault(size = 30) Pageable pageable) {

        return notifications.list(
                workspaceId, CurrentUser.requireId(), widensReading(workspaceId), unread, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many of yours are unread", description = "The badge")
    Map<String, Long> unreadCount(@PathVariable UUID workspaceId) {
        workspaceGuard.visiblePermissions(workspaceId);
        return Map.of("unread", notifications.unreadCount(workspaceId, CurrentUser.requireId()));
    }

    /**
     * Marks one read.
     *
     * <p>{@code PATCH} rather than {@code POST}, because it changes one field of one resource. It is
     * idempotent: a second call succeeds and leaves the original moment alone.
     */
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark one read", description = "Idempotent. Somebody else's answers 404")
    void markRead(@PathVariable UUID workspaceId, @PathVariable UUID notificationId) {
        workspaceGuard.visiblePermissions(workspaceId);
        notifications.markRead(workspaceId, CurrentUser.requireId(), notificationId);
    }

    /**
     * Clears the badge.
     *
     * <p>{@code POST} rather than {@code PATCH}, because it names no resource and is an action across
     * a collection. It reports how many rows it moved, which is what lets a client decide whether to
     * refresh the list it is showing.
     */
    @PostMapping("/read-all")
    @Operation(summary = "Mark everything read", description = "Returns how many were still unread")
    Map<String, Integer> markAllRead(@PathVariable UUID workspaceId) {
        workspaceGuard.visiblePermissions(workspaceId);
        return Map.of("marked", notifications.markAllRead(workspaceId, CurrentUser.requireId()));
    }

    /**
     * Membership, and the one thing the permission set is used for here.
     *
     * <p>{@code project:read_any} does not widen whose notifications the caller can read. It widens
     * which of their own still name a task, because somebody who can see every project can still see
     * the work their older notifications point at.
     */
    private boolean widensReading(UUID workspaceId) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        return granted.contains(Permissions.PROJECT_READ_ANY);
    }
}
