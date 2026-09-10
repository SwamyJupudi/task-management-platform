package com.company.taskmanagementplatform.teams;

import java.util.UUID;

/**
 * Raised when a team is removed.
 *
 * <p>Published before the row is soft deleted and listened for inside the publishing transaction,
 * matching {@code WorkspaceMemberRemovedEvent}. Anything holding a reference to the team stands down
 * on this rather than being left pointing at a row that every read now filters out.
 *
 * <p>Public, unlike most events in this package, because the modules that have to react to it are
 * outside it.
 */
public record TeamDeletedEvent(UUID workspaceId, UUID teamId) {}
