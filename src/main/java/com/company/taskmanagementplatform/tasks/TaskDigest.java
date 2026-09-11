package com.company.taskmanagementplatform.tasks;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The facts about one task that another module needs to talk about it.
 *
 * <p>Built for {@code notifications}, which has two questions this answers and no business loading a
 * task to ask them: who should hear about a change to this task, and what should the message call
 * it.
 *
 * <p>Deliberately not the entity, for the reason every facade in this platform gives. The facade
 * runs in its own read-only transaction, so an entity handed out of this module would arrive
 * detached and every change made to it would be discarded without an error.
 */
public record TaskDigest(
        UUID taskId,
        UUID workspaceId,
        UUID projectId,
        int taskNumber,
        String title,
        UUID assigneeUserId,
        UUID reporterUserId,
        LocalDate dueDate) {}
