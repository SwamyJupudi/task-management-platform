package com.company.taskmanagementplatform.projects;

import java.time.Instant;
import java.util.UUID;

/**
 * The facts about one project that another module needs to report on it.
 *
 * <p>Built for {@code reports}, which renders a project's progress beside counts it derives from the
 * tasks module. It carries the derived {@code progress} column rather than recomputing anything: the
 * rule that fills that column lives in one statement in this package and phase eight reads it.
 *
 * <p>Deliberately not the entity, for the reason every facade in this platform gives. The facade
 * runs in its own read-only transaction, so an entity handed out of this module would arrive
 * detached and every change made to it would be discarded without an error.
 */
public record ProjectSummary(
        UUID projectId,
        UUID workspaceId,
        String key,
        String name,
        ProjectStatus status,
        UUID ownerUserId,
        UUID teamId,
        int progress,
        Instant updatedAt) {}
