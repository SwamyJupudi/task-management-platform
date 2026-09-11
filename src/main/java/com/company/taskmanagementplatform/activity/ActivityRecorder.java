package com.company.taskmanagementplatform.activity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes one audit row, in its own transaction, on the activity module's own thread.
 *
 * <p>{@link Propagation#REQUIRES_NEW} rather than the default, because by the time this runs there
 * is no transaction to join: the caller's has committed and this is happening afterwards, on another
 * thread. Without it the insert would be auto-committed with no boundary at all.
 *
 * <p>Both the moment and the correlation id are passed in rather than read here. They belong to the
 * request that caused the action, and this runs after that request has moved on: {@code
 * Instant.now()} here would record when the queue got to the row, and the logging context on this
 * thread belongs to nobody.
 */
@Component
class ActivityRecorder {

    private final ActivityLogRepository logs;
    private final JsonMapper json;

    ActivityRecorder(ActivityLogRepository logs, JsonMapper json) {
        this.logs = logs;
        this.json = json;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(
            UUID workspaceId,
            UUID actorUserId,
            String action,
            ActivityEntityType entityType,
            UUID entityId,
            UUID projectId,
            Map<String, Object> metadata,
            String requestId,
            Instant occurredAt) {

        logs.save(new ActivityLog(
                workspaceId,
                actorUserId,
                action,
                entityType,
                entityId,
                projectId,
                serialise(metadata),
                requestId,
                occurredAt));
    }

    /**
     * Turns the facts of an action into the JSON the row carries.
     *
     * <p>Null values are dropped rather than written. "The assignee is now nobody" is said by the
     * absence of an assignee in a {@code task.assigned} row, and a field of explicit nulls would make
     * every row larger for no reader's benefit.
     */
    private String serialise(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        Map<String, Object> present = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (value != null) {
                present.put(key, value instanceof UUID id ? id.toString() : value);
            }
        });

        return present.isEmpty() ? null : json.writeValueAsString(present);
    }
}
