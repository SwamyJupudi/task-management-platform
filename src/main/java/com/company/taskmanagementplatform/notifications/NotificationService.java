package com.company.taskmanagementplatform.notifications;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.notifications.dto.NotificationResponse;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.tasks.TaskDigest;
import com.company.taskmanagementplatform.tasks.TaskNotificationFacade;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Reading one person's feed, and marking it read. There is no method here that creates one.
 *
 * <p>Writing happens in {@link NotificationWriter}, from events and from the scan, and nothing
 * outside this module can reach either. Who hears what is the system's decision, not an API call.
 *
 * <p><strong>Every method takes the viewer and every query is narrowed to them.</strong> There is no
 * administrative variant and no permission that widens this, at any role including the platform
 * administrator. A notification has one audience. The administrative question, who was told what, is
 * the audit trail's and it already answers it.
 *
 * <p>The viewer is passed in rather than read from the security context here, so that the rule is
 * visible in the signature and the service can be reasoned about without knowing what a filter put
 * in a thread local.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final TaskNotificationFacade tasks;
    private final ProjectAccessFacade projects;
    private final UserAccountService users;
    private final JsonMapper json;
    private final Clock clock;

    NotificationService(
            NotificationRepository notifications,
            TaskNotificationFacade tasks,
            ProjectAccessFacade projects,
            UserAccountService users,
            JsonMapper json,
            Clock clock) {
        this.notifications = notifications;
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
        this.json = json;
        this.clock = clock;
    }

    /**
     * One page of somebody's feed, newest first.
     *
     * @param unreadOnly narrows to what is still unread, which is the badge's own listing
     * @param unrestricted whether the viewer holds {@code project:read_any}, which decides how much
     *     of their own feed still resolves to a name
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(
            UUID workspaceId, UUID viewerUserId, boolean unrestricted, boolean unreadOnly, Pageable pageable) {

        Page<Notification> page = unreadOnly
                ? notifications.findAllByRecipientUserIdAndWorkspaceIdAndReadAtIsNullOrderByCreatedAtDesc(
                        viewerUserId, workspaceId, pageable)
                : notifications.findAllByRecipientUserIdAndWorkspaceIdOrderByCreatedAtDesc(
                        viewerUserId, workspaceId, pageable);

        return map(page, workspaceId, viewerUserId, unrestricted);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID workspaceId, UUID viewerUserId) {
        return notifications.countByRecipientUserIdAndWorkspaceIdAndReadAtIsNull(viewerUserId, workspaceId);
    }

    /**
     * Marks one read.
     *
     * <p>Idempotent: marking an already-read notification succeeds and moves nothing, so a client
     * that retries does not rewrite the moment somebody first saw it.
     *
     * @throws ResourceNotFoundException if it is not theirs, which is deliberately the same answer as
     *     if it did not exist
     */
    @Transactional
    public void markRead(UUID workspaceId, UUID viewerUserId, UUID notificationId) {
        notifications
                .findByIdAndRecipientUserIdAndWorkspaceId(notificationId, viewerUserId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId))
                .markRead(clock.instant());
    }

    /** @return how many were still unread and have now been marked */
    @Transactional
    public int markAllRead(UUID workspaceId, UUID viewerUserId) {
        return notifications.markAllRead(viewerUserId, workspaceId, clock.instant());
    }

    /**
     * A page of rows into a page of messages, in a fixed number of queries.
     *
     * <p>Four, whatever the page size: the actors, the tasks, the project keys, and the viewer's
     * project scope. A lookup per row is how a feed becomes N+1, and a feed is the one listing
     * somebody reloads all day.
     */
    private PageResponse<NotificationResponse> map(
            Page<Notification> page, UUID workspaceId, UUID viewerUserId, boolean unrestricted) {

        List<Notification> rows = page.getContent();

        Map<UUID, UserAccount> actors = users.findAllByIds(rows.stream()
                .map(Notification::getActorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        Map<UUID, Map<String, Object>> metadata = new LinkedHashMap<>();
        rows.forEach(row -> metadata.put(row.getId(), parse(row.getMetadata())));

        Map<UUID, TaskDigest> taskDigests = tasks.findAllByIds(rows.stream()
                .map(row -> taskIdOf(row, metadata.get(row.getId())))
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        Map<UUID, String> projectKeys = projects.keysOf(taskDigests.values().stream()
                .map(TaskDigest::projectId)
                .distinct()
                .toList());

        ProjectScope visible = projects.readableScope(workspaceId, viewerUserId, unrestricted);
        Set<UUID> reachable = new LinkedHashSet<>(visible.projectIds());

        List<NotificationResponse> content = rows.stream()
                .map(row -> build(row, metadata.get(row.getId()), actors, taskDigests, projectKeys, visible, reachable))
                .toList();

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private NotificationResponse build(
            Notification row,
            Map<String, Object> metadata,
            Map<UUID, UserAccount> actors,
            Map<UUID, TaskDigest> taskDigests,
            Map<UUID, String> projectKeys,
            ProjectScope visible,
            Set<UUID> reachable) {

        UserAccount actor = row.getActorUserId() == null ? null : actors.get(row.getActorUserId());
        String actorName = actor == null ? null : actor.fullName();

        TaskDigest task = visibleTask(row, metadata, taskDigests, visible, reachable);
        String taskKey = task == null ? null : key(projectKeys.get(task.projectId()), task.taskNumber());
        String title = task == null ? null : task.title();

        return new NotificationResponse(
                row.getId(),
                row.getType(),
                NotificationMessages.render(row.getType(), actorName, title, metadata),
                row.getActorUserId(),
                actorName,
                row.getEntityType(),
                row.getEntityId(),
                row.getProjectId(),
                taskKey,
                metadata,
                row.getReadAt(),
                row.getCreatedAt());
    }

    /**
     * The task behind a row, if the viewer may still see it.
     *
     * <p><strong>This is the render-time access check, and it is the reason a feed cannot leak.</strong>
     * A notification is written to somebody who could see the work at the time. Access can be taken
     * away afterwards: removed from a project, moved to another team. The cleanup listener deletes
     * what it can reach, but a task moved out of reach without anybody being removed is not an event
     * anything publishes, so the read path checks rather than trusting the row.
     *
     * <p>A task the viewer can no longer reach reads as no task at all, which degrades the message to
     * "somebody commented on a task" and leaves no key to follow.
     */
    private TaskDigest visibleTask(
            Notification row,
            Map<String, Object> metadata,
            Map<UUID, TaskDigest> taskDigests,
            ProjectScope visible,
            Set<UUID> reachable) {

        UUID taskId = taskIdOf(row, metadata);
        if (taskId == null) {
            return null;
        }

        TaskDigest task = taskDigests.get(taskId);
        if (task == null) {
            return null;
        }

        return visible.unrestricted() || reachable.contains(task.projectId()) ? task : null;
    }

    /**
     * Which task a row is about.
     *
     * <p>A task notification points at the task itself; a comment notification points at the comment
     * and carries its task in metadata, because what a client opens is the comment but what a person
     * recognises is the task it is on.
     */
    private UUID taskIdOf(Notification row, Map<String, Object> metadata) {
        if (NotificationEntityType.TASK.name().equals(row.getEntityType())) {
            return row.getEntityId();
        }

        Object taskId = metadata.get("taskId");
        if (taskId == null) {
            return null;
        }

        try {
            return UUID.fromString(taskId.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String key(String projectKey, int taskNumber) {
        return projectKey == null ? null : projectKey + "-" + taskNumber;
    }

    /**
     * Metadata back into a map.
     *
     * <p>A row whose JSON cannot be read is still worth showing: the type, the actor and the moment
     * are all in columns. Returning an empty map keeps one malformed entry from breaking a page.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(metadata, Map.class);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
