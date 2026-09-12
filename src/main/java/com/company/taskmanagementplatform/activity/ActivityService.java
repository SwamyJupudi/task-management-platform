package com.company.taskmanagementplatform.activity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.activity.dto.ActivityResponse;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.tasks.TaskRef;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Reading the audit trail. There is no method here that writes one.
 *
 * <p>Writing happens in {@link ActivityRecorder}, from events, and nothing outside this module can
 * reach either. That is what "audit records should not be casually editable" means at the
 * application layer; the trigger in {@code V7} is what means it at the database layer.
 *
 * <p>Every listing is newest first, which is the only order a history is read in.
 */
@Service
public class ActivityService {

    private final ActivityLogRepository logs;
    private final UserAccountService users;
    private final JsonMapper json;

    ActivityService(
            ActivityLogRepository logs,
            UserAccountService users,
            JsonMapper json) {
        this.logs = logs;
        this.users = users;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forWorkspace(UUID workspaceId, Pageable pageable) {
        return map(logs.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable));
    }

    /**
     * The platform audit trail: the rows that belong to no workspace.
     *
     * <p>Account administration and platform-role grants. Every row here was written by somebody
     * holding a platform role, and reading it needs one too. It is deliberately disjoint from {@link
     * #forWorkspace}: a row appears in exactly one of the two, decided by whether its workspace is
     * null, so neither listing can ever be a way into the other.
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forPlatform(Pageable pageable) {
        return map(logs.findAllByWorkspaceIdIsNullOrderByCreatedAtDesc(pageable));
    }

    /** How many audit rows were written since a moment, for the platform statistics panel. */
    @Transactional(readOnly = true)
    public long countSince(java.time.Instant since) {
        return logs.countByCreatedAtGreaterThanEqual(since);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forProject(UUID workspaceId, UUID projectId, Pageable pageable) {
        return map(logs.findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(workspaceId, projectId, pageable));
    }

    /**
     * One person's own recent actions, for their dashboard.
     *
     * <p>Needs no permission beyond membership, and widens nothing: every row names the caller as the
     * actor. Browsing what anybody else did is still {@code activity:read}, which only an
     * administrator holds, and one record's history is still gated by being able to see that record.
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forActor(UUID workspaceId, UUID actorUserId, Pageable pageable) {
        return map(logs.findAllByWorkspaceIdAndActorUserIdOrderByCreatedAtDesc(workspaceId, actorUserId, pageable));
    }

    /**
     * One task's history, including what happened to its comments, files and checklist.
     *
     * <p>A task history that showed only rows whose entity is the task would have no comments in it,
     * and the requirements print "Anil added a comment" as an example of exactly what belongs there.
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forTask(TaskRef task, Pageable pageable) {
        return map(logs.findTaskHistory(task.workspaceId(), task.projectId(), task.taskId(), pageable));
    }

    /** One query for every actor on the page, for the reason every other mapper in the platform gives. */
    private PageResponse<ActivityResponse> map(Page<ActivityLog> page) {
        List<UUID> actorIds = page.getContent().stream()
                .map(ActivityLog::getActorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UserAccount> actors = users.findAllByIds(actorIds);

        List<ActivityResponse> content = page.getContent().stream()
                .map(entry -> build(entry, entry.getActorUserId() == null ? null : actors.get(entry.getActorUserId())))
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

    private ActivityResponse build(ActivityLog entry, UserAccount actor) {
        Map<String, Object> metadata = parse(entry.getMetadata());
        String actorName = actor == null ? null : actor.fullName();

        return new ActivityResponse(
                entry.getId(),
                entry.getWorkspaceId(),
                entry.getActorUserId(),
                actor == null ? null : actor.email(),
                actorName,
                entry.getAction(),
                entry.getEntityType().name(),
                entry.getEntityId(),
                entry.getProjectId(),
                metadata,
                ActivitySummaries.render(entry.getAction(), actorName, metadata),
                entry.getRequestId(),
                entry.getCreatedAt());
    }

    /**
     * Metadata back into a map.
     *
     * <p>A row whose JSON cannot be read is still a row worth showing: the action, the actor and the
     * moment are all in columns. Returning an empty map keeps one malformed entry from breaking a
     * whole page of history.
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
