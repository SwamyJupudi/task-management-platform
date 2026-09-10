package com.company.taskmanagementplatform.tasks;

import java.util.UUID;

/**
 * One authorized task, named by the three identifiers everything downstream needs.
 *
 * <p>Handed back by {@link TaskAccessGuard} so that the caller does not have to load the task again
 * only to discover which project it is in. Deliberately not the entity: the guard runs in its own
 * read-only transaction, so an entity from it would arrive detached and every change made to it
 * would be discarded without an error.
 *
 * <p>Public because the {@code subtasks} module authorizes through the parent task and needs to know
 * where the parent lives.
 */
public record TaskRef(UUID taskId, UUID projectId, UUID workspaceId) {}
