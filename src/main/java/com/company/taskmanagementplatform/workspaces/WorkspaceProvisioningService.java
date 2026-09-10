package com.company.taskmanagementplatform.workspaces;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * Creates workspaces and lists them for the platform administrator.
 *
 * <p>A new workspace gets its three roles in the same transaction, so one that exists without them
 * is not a state the system can reach.
 *
 * <p>The creator is not added as a member, which looks like an omission and is not. Only a platform
 * administrator may create a workspace, and a platform administrator already reaches every workspace
 * through its platform role. Adding a membership row as well would create a second, redundant source
 * of their access, and the first thing anybody would then ask is which of the two is authoritative.
 * The first act inside a new workspace is inviting somebody to administer it.
 */
@Service
public class WorkspaceProvisioningService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceRoleSeeder roleSeeder;

    WorkspaceProvisioningService(WorkspaceRepository workspaces, WorkspaceRoleSeeder roleSeeder) {
        this.workspaces = workspaces;
        this.roleSeeder = roleSeeder;
    }

    @Transactional
    public WorkspaceResponse create(String name, String slug, UUID creatorUserId) {
        if (workspaces.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new ConflictException("A workspace with that address already exists.");
        }

        Workspace workspace = workspaces.save(Workspace.create(name.trim(), slug, creatorUserId));
        roleSeeder.seed(workspace.getId());
        return toResponse(workspace);
    }

    @Transactional(readOnly = true)
    public Page<WorkspaceResponse> listAll(Pageable pageable) {
        return workspaces.findAllByDeletedAtIsNull(pageable).map(WorkspaceProvisioningService::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID workspaceId) {
        return workspaces.findByIdAndDeletedAtIsNull(workspaceId)
                .map(WorkspaceProvisioningService::toResponse)
                .orElseThrow(() -> com.company.taskmanagementplatform.common.error.ResourceNotFoundException.of(
                        "Workspace", workspaceId));
    }

    static WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getStatus().name(),
                workspace.getCreatedAt());
    }
}
