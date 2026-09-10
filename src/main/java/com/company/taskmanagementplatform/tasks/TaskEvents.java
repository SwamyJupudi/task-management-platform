package com.company.taskmanagementplatform.tasks;

import java.util.UUID;

/**
 * What this module announces, for the modules that record and relay it.
 *
 * <p>Published inside the publishing transaction and, in this phase, listened for by nobody. The
 * {@code activity} module writes the audit records in phase six and {@code notifications} sends from
 * the same events in phase seven. Neither appears in this module's imports, which is the point: the
 * publisher does not know or care who is listening.
 *
 * <p>Two of these are obligations rather than choices. The requirements name "task assigned or
 * status changes" among the events that must produce a notification, so {@link TaskAssigned} and
 * {@link TaskStatusChanged} exist because a later phase is required to be able to hear them.
 *
 * <p>Unassignment is {@link TaskAssigned} with a null new assignee rather than an event of its own,
 * so a listener has one thing to subscribe to and cannot handle half the cases.
 *
 * <p>Grouped in one file because they are one vocabulary. Each carries identifiers and the values
 * that changed, never an entity: an entity on an event outlives the transaction that loaded it.
 */
public final class TaskEvents {

    private TaskEvents() {}

    public record TaskCreated(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            int taskNumber,
            String title,
            UUID assigneeUserId,
            UUID actorUserId) {}

    public record TaskUpdated(UUID workspaceId, UUID projectId, UUID taskId, UUID actorUserId) {}

    /** Carries both people, because "assigned to Rahul" is not a useful audit line on its own. */
    public record TaskAssigned(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID previousAssigneeUserId,
            UUID newAssigneeUserId,
            UUID actorUserId) {}

    /** Carries both statuses, for the same reason the project one does. */
    public record TaskStatusChanged(
            UUID workspaceId, UUID projectId, UUID taskId, TaskStatus from, TaskStatus to, UUID actorUserId) {}

    public record TaskDeleted(UUID workspaceId, UUID projectId, UUID taskId, UUID actorUserId) {}

    public record TaskDependencyAdded(
            UUID workspaceId, UUID projectId, UUID taskId, UUID dependsOnTaskId, UUID actorUserId) {}

    public record TaskDependencyRemoved(
            UUID workspaceId, UUID projectId, UUID taskId, UUID dependsOnTaskId, UUID actorUserId) {}
}
