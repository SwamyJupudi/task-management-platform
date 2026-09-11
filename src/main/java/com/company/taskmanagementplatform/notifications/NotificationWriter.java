package com.company.taskmanagementplatform.notifications;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes notification rows, in its own transaction, on the notifications module's own thread.
 *
 * <p>{@link Propagation#REQUIRES_NEW} rather than the default, because by the time this runs there
 * is usually no transaction to join: the caller's has committed and this is happening afterwards, on
 * another thread. Without it the insert would be auto-committed with no boundary at all, and a
 * multi-recipient write would stop being one unit.
 *
 * <p>The moment is passed in rather than read here, for the reason the audit trail gives: it belongs
 * to the request that caused the action, and {@code Instant.now()} on this thread would record when
 * the queue got to the row.
 *
 * @see NotificationRepository#insert for why the insert is native
 */
@Component
class NotificationWriter {

    private final NotificationRepository notifications;
    private final JsonMapper json;

    NotificationWriter(NotificationRepository notifications, JsonMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    /**
     * One action's worth of notifications.
     *
     * <p>All of them in one transaction, so a project status change either tells everybody or tells
     * nobody and is logged. Rows the database skips as duplicates are not failures and are counted
     * as written nothing.
     *
     * @return how many rows were actually inserted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int writeAll(Collection<NotificationRow> rows, Instant occurredAt) {
        int written = 0;
        for (NotificationRow row : rows) {
            written += notifications.insert(
                    row.workspaceId(),
                    row.recipientUserId(),
                    row.actorUserId(),
                    row.type().code(),
                    row.type().entityType().name(),
                    row.entityId(),
                    row.projectId(),
                    serialise(row.metadata()),
                    row.dedupeKey(),
                    occurredAt);
        }
        return written;
    }

    /**
     * Turns the facts of a notification into the JSON the row carries.
     *
     * <p>Null values are dropped rather than written, exactly as the audit trail drops them. A field
     * of explicit nulls would make every row larger for no reader's benefit.
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

    /**
     * One row waiting to be written.
     *
     * <p>Identifiers and values, never an entity: an entity on one of these would outlive the
     * transaction that loaded it, and these are built on one thread and written on another.
     */
    record NotificationRow(
            UUID workspaceId,
            UUID recipientUserId,
            UUID actorUserId,
            NotificationType type,
            UUID entityId,
            UUID projectId,
            Map<String, Object> metadata,
            String dedupeKey) {

        static NotificationRow of(
                UUID workspaceId,
                UUID recipientUserId,
                UUID actorUserId,
                NotificationType type,
                UUID entityId,
                UUID projectId,
                Map<String, Object> metadata) {

            return new NotificationRow(
                    workspaceId, recipientUserId, actorUserId, type, entityId, projectId, metadata, null);
        }

        static List<NotificationRow> forEach(
                Collection<UUID> recipients,
                UUID workspaceId,
                UUID actorUserId,
                NotificationType type,
                UUID entityId,
                UUID projectId,
                Map<String, Object> metadata) {

            return recipients.stream()
                    .map(recipient -> of(workspaceId, recipient, actorUserId, type, entityId, projectId, metadata))
                    .toList();
        }
    }
}
