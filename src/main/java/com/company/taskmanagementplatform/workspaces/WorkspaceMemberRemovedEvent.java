package com.company.taskmanagementplatform.workspaces;

import java.util.UUID;

/**
 * Raised when somebody stops being a member of a workspace.
 *
 * <p>Published <em>before</em> the membership row is deleted, and listened for inside the publishing
 * transaction. Both halves of that matter. The schema makes a team lead and a team member foreign
 * keys into {@code workspace_members}, so the rows that depend on the membership have to go first or
 * the database refuses the delete. Publishing early is what gives the modules that own those rows
 * their chance to stand down.
 *
 * <p>Public, unlike the other events in this package, because the module that has to react to it is
 * {@code teams} rather than this one.
 */
public record WorkspaceMemberRemovedEvent(UUID workspaceId, UUID userId) {}
