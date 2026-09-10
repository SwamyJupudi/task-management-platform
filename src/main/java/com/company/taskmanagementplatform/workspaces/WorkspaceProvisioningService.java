package com.company.taskmanagementplatform.workspaces;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
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
 *
 * <p>Editing and archiving a workspace live in {@link WorkspaceLifecycleService}.
 */
@Service
public class WorkspaceProvisioningService {

    private final WorkspaceRepository workspaces;
    private final RoleRepository roles;
    private final WorkspaceRoleSeeder roleSeeder;

    WorkspaceProvisioningService(
            WorkspaceRepository workspaces, RoleRepository roles, WorkspaceRoleSeeder roleSeeder) {
        this.workspaces = workspaces;
        this.roles = roles;
        this.roleSeeder = roleSeeder;
    }

    @Transactional
    public WorkspaceResponse create(String name, String slug, UUID creatorUserId) {
        if (workspaces.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new ConflictException("A workspace with that address already exists.");
        }

        Workspace workspace = workspaces.save(Workspace.create(name.trim(), slug, creatorUserId));
        Map<SystemRole, Role> seeded = roleSeeder.seed(workspace.getId());

        // Somebody invited without a named role joins as an employee. Set here
        // rather than left null so the setting means something from the first
        // invitation onwards.
        Role fallback = seeded.get(SystemRole.EMPLOYEE);
        workspace.useDefaultRole(fallback.getId());

        return WorkspaceMapper.toResponse(workspace, fallback.getSlug());
    }

    @Transactional(readOnly = true)
    public Page<WorkspaceResponse> listAll(Pageable pageable) {
        Page<Workspace> page = workspaces.findAllByDeletedAtIsNull(pageable);
        Map<UUID, String> slugsById = defaultRoleSlugs(page.getContent());

        return page.map(workspace ->
                WorkspaceMapper.toResponse(workspace, slugsById.get(workspace.getDefaultRoleId())));
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID workspaceId) {
        Workspace workspace = workspaces
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace", workspaceId));

        return WorkspaceMapper.toResponse(workspace, defaultRoleSlug(workspace));
    }

    /** One read for the whole page rather than one per row, which is how a list turns into N+1. */
    private Map<UUID, String> defaultRoleSlugs(List<Workspace> page) {
        List<UUID> roleIds = page.stream()
                .map(Workspace::getDefaultRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return roleIds.isEmpty()
                ? Map.of()
                : roles.findAllById(roleIds).stream().collect(Collectors.toMap(Role::getId, Role::getSlug));
    }

    private String defaultRoleSlug(Workspace workspace) {
        return workspace.getDefaultRoleId() == null
                ? null
                : roles.findById(workspace.getDefaultRoleId())
                        .map(Role::getSlug)
                        .orElse(null);
    }
}
