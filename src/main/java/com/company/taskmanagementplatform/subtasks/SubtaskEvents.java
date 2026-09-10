package com.company.taskmanagementplatform.subtasks;

import java.util.UUID;

import com.company.taskmanagementplatform.tasks.TaskStatus;

/**
 * What this module announces, for the modules that record and relay it.
 *
 * <p>Published inside the publishing transaction and, in this phase, listened for by nobody. The
 * {@code activity} module writes the audit records in phase six and {@code notifications} sends from
 * the same events in phase seven.
 *
 * <p>{@link SubtaskCompleted} is published alongside {@link SubtaskStatusChanged} when the move is to
 * DONE, and the redundancy is deliberate. Completion is the requirements' own word for what a
 * subtask tracks, and a listener that wants to say "Rahul finished a subtask" should not have to know
 * that finishing is a particular value of a status field.
 */
public final class SubtaskEvents {

    private SubtaskEvents() {}

    public record SubtaskCreated(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID subtaskId,
            String title,
            UUID assigneeUserId,
            UUID actorUserId) {}

    public record SubtaskUpdated(
            UUID workspaceId, UUID projectId, UUID taskId, UUID subtaskId, UUID actorUserId) {}

    public record SubtaskStatusChanged(
            UUID workspaceId,
            UUID projectId,
            UUID taskId,
            UUID subtaskId,
            TaskStatus from,
            TaskStatus to,
            UUID actorUserId) {}

    /** The DONE case of the above, named the way the requirements name it. */
    public record SubtaskCompleted(
            UUID workspaceId, UUID projectId, UUID taskId, UUID subtaskId, UUID actorUserId) {}

    public record SubtaskDeleted(
            UUID workspaceId, UUID projectId, UUID taskId, UUID subtaskId, UUID actorUserId) {}
}
