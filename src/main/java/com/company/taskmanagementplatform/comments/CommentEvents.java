package com.company.taskmanagementplatform.comments;

import java.util.List;
import java.util.UUID;

/**
 * What this module announces, for the modules that record and relay it.
 *
 * <p>Published inside the publishing transaction. The {@code activity} module writes the audit
 * records from these in this phase, the {@code attachments} module hears {@link CommentDeleted} to
 * take a comment's files with it, and {@code notifications} will send from the same events in phase
 * seven. None of them appears in this module's imports.
 *
 * <p>{@link UserMentioned} is a separate event from {@link CommentCreated} rather than a field on
 * it, because the requirements name "user mentioned" as its own notification trigger with its own
 * recipient. One event per person is what lets a listener subscribe to the thing it actually cares
 * about instead of unpacking a list and rediscovering the rule.
 *
 * <p>Grouped in one file because they are one vocabulary. Each carries identifiers and the values
 * that changed, never an entity: an entity on an event outlives the transaction that loaded it.
 */
public final class CommentEvents {

    private CommentEvents() {}

    public record CommentCreated(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID commentId,
            List<UUID> mentionedUserIds,
            UUID actorUserId) {}

    /**
     * An edit, carrying only the people the edit newly named.
     *
     * <p>Somebody already mentioned in the previous version has already been told, and telling them
     * again because a typo was fixed is how a notification feed becomes something people stop
     * reading.
     */
    public record CommentUpdated(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID commentId,
            List<UUID> newlyMentionedUserIds,
            UUID actorUserId) {}

    public record CommentDeleted(UUID workspaceId, UUID projectId, UUID taskId, UUID commentId, UUID actorUserId) {}

    /** One person named in one comment, which is the shape a notification actually needs. */
    public record UserMentioned(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID commentId,
            UUID mentionedUserId,
            UUID actorUserId) {}
}
