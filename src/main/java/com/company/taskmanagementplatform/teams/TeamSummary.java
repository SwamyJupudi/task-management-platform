package com.company.taskmanagementplatform.teams;

import java.util.UUID;

/**
 * The facts about one team that another module needs to report on it.
 *
 * <p>The lead is nullable, because a team between leads is an ordinary state rather than an error,
 * which is the same reason the column itself is nullable.
 *
 * <p>Deliberately not the entity, for the reason every facade in this platform gives. The facade
 * runs in its own read-only transaction, so an entity handed out of this module would arrive
 * detached and every change made to it would be discarded without an error.
 */
public record TeamSummary(UUID teamId, String name, UUID leadUserId, TeamStatus status, long memberCount) {}
