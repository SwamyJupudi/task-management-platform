package com.company.taskmanagementplatform.workspaces;

/**
 * Whether a role applies to the whole installation or inside one workspace.
 *
 * <p>The database holds the two apart with a check constraint: platform scope requires no workspace,
 * workspace scope requires one. That constraint is also why a workspace member cannot be given a
 * platform role, since the composite key the membership references has no null half to match.
 */
public enum RoleScope {
    PLATFORM,
    WORKSPACE
}
