package com.company.taskmanagementplatform.workspaces;

import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * The one place a workspace becomes a response body.
 *
 * <p>Two services produce workspaces now, provisioning and lifecycle, and a response assembled
 * independently in each would drift the first time a field was added to only one of them.
 */
final class WorkspaceMapper {

    private WorkspaceMapper() {}

    /**
     * @param defaultRoleSlug the slug of the workspace's default role, or null if it has none
     */
    static WorkspaceResponse toResponse(Workspace workspace, String defaultRoleSlug) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getDescription(),
                workspace.getTimezone(),
                defaultRoleSlug,
                workspace.getStatus().name(),
                workspace.getArchivedAt(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }
}
