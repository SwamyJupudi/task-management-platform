package com.company.taskmanagementplatform.attachments;

import java.util.UUID;

/**
 * What this module announces, for the modules that record and relay it.
 *
 * <p>Published inside the publishing transaction, and listened for by {@code activity} in this
 * phase. Each carries the filename as it was at the time, because an audit line reading "removed an
 * attachment" without saying which one is not worth the row it occupies.
 *
 * <p>Grouped in one file because they are one vocabulary. Each carries identifiers and values, never
 * an entity: an entity on an event outlives the transaction that loaded it.
 */
public final class AttachmentEvents {

    private AttachmentEvents() {}

    public record AttachmentUploaded(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID attachmentId,
            String filename,
            long sizeBytes,
            UUID actorUserId) {}

    public record AttachmentDeleted(
            UUID workspaceId, UUID projectId, UUID taskId, UUID attachmentId, String filename, UUID actorUserId) {}
}
