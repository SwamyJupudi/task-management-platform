package com.company.taskmanagementplatform.projects;

import java.util.UUID;

/**
 * What this module announces, for the modules that record and relay it.
 *
 * <p>Published inside the publishing transaction and, in this phase, listened for by nobody. The
 * {@code activity} module writes the audit records in phase six and {@code notifications} sends from
 * the same events in phase seven. Neither appears in this module's imports, which is the point: the
 * publisher does not know or care who is listening.
 *
 * <p>Two of these are obligations rather than choices. The requirements name "user added to a
 * project" and "project status changes" among the events that must produce a notification, so {@link
 * ProjectMemberAdded} and {@link ProjectStatusChanged} exist because a later phase is required to be
 * able to hear them.
 *
 * <p>Grouped in one file because they are one vocabulary. Each carries identifiers and the values
 * that changed, never an entity: an entity on an event outlives the transaction that loaded it.
 */
public final class ProjectEvents {

    private ProjectEvents() {}

    public record ProjectCreated(UUID workspaceId, UUID projectId, String key, String name, UUID actorUserId) {}

    public record ProjectUpdated(UUID workspaceId, UUID projectId, UUID actorUserId) {}

    /** Carries both statuses, because "changed to ACTIVE" is not a useful audit line on its own. */
    public record ProjectStatusChanged(
            UUID workspaceId, UUID projectId, ProjectStatus from, ProjectStatus to, UUID actorUserId) {}

    public record ProjectOwnerChanged(
            UUID workspaceId, UUID projectId, UUID previousOwnerUserId, UUID newOwnerUserId, UUID actorUserId) {}

    public record ProjectMemberAdded(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {}

    public record ProjectMemberRemoved(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {}

    public record ProjectDeleted(UUID workspaceId, UUID projectId, UUID actorUserId) {}
}
