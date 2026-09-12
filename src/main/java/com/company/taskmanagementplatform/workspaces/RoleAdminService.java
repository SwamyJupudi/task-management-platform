package com.company.taskmanagementplatform.workspaces;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.workspaces.dto.RoleResponse;

/**
 * Editing what a role grants. The thing {@code RoleQueryService} has been saying belongs to the
 * admin panel since phase two.
 *
 * <p>Separate from that class for the reason every read/write split in this platform is made: one
 * reads and pages and publishes nothing, the other writes and publishes. Two personalities on one
 * class is how a read accidentally gains a side effect.
 *
 * <p>{@code role:manage} has been in the catalog and granted to {@code ADMIN} since phase two with
 * nothing checking it. This is the endpoint it was seeded for.
 *
 * <p><strong>Replacement, not a delta.</strong> The caller sends the set the role should hold
 * afterwards and gets back the role as it now stands, so nobody has to guess. The audit row carries
 * the difference rather than the result, because "what changed" is the question an audit trail
 * answers and a row listing forty final codes answers a different one.
 *
 * <p><strong>A platform role cannot be edited through here at all</strong>, and that is the schema's
 * doing rather than a check below. Roles are looked up by {@code (workspaceId, slug)}; a platform
 * role has a null workspace and so matches no such pair. {@code SUPER_ADMIN} is therefore
 * unreachable from every workspace path, which is the same guarantee that stops a workspace member
 * being assigned it.
 */
@Service
public class RoleAdminService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final WorkspaceMemberRepository members;
    private final ApplicationEventPublisher events;

    RoleAdminService(
            RoleRepository roles,
            PermissionRepository permissions,
            RolePermissionRepository rolePermissions,
            WorkspaceMemberRepository members,
            ApplicationEventPublisher events) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.members = members;
        this.events = events;
    }

    /**
     * Replaces a role's grants with exactly the codes given.
     *
     * @param requested the complete set the role should hold afterwards. Empty is legal and means
     *     the role grants nothing; duplicates are folded rather than refused, since asking for the
     *     same code twice is not a mistake worth failing a request over
     * @throws ResourceNotFoundException if the workspace has no role with that slug
     * @throws BadRequestException naming the codes, if any is not in the catalog
     * @throws ConflictException if the caller would lose the ability to edit roles
     */
    @Transactional
    public RoleResponse replacePermissions(UUID workspaceId, String roleSlug, Collection<String> requested) {
        Role role = roles.findByWorkspaceIdAndSlug(workspaceId, roleSlug)
                .orElseThrow(() -> ResourceNotFoundException.of("Role", roleSlug));

        Set<String> wanted = new LinkedHashSet<>(requested);

        Map<String, UUID> idsByCode = permissions.findAllByCodeIn(wanted).stream()
                .collect(Collectors.toMap(Permission::getCode, Permission::getId));

        // Every code checked before anything is written, so an unknown one leaves
        // the role exactly as it was rather than half replaced.
        List<String> unknown = wanted.stream()
                .filter(code -> !idsByCode.containsKey(code))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException(
                    "These are not permissions this application implements: " + String.join(", ", unknown) + ".");
        }

        Set<String> current = currentCodes(role.getId());
        requireCallerKeepsRoleManagement(workspaceId, role, wanted);

        List<String> added = wanted.stream().filter(code -> !current.contains(code)).sorted().toList();
        List<String> removed = current.stream().filter(code -> !wanted.contains(code)).sorted().toList();

        // Read before the delete clears the persistence context and detaches the
        // role. Every one is a basic column already loaded, so nothing here
        // touches the database again, but reading them afterwards would rely on
        // that rather than state it.
        UUID roleId = role.getId();
        String slug = role.getSlug();
        String name = role.getName();
        String scope = role.getScope().name();
        boolean system = role.isSystem();

        if (!added.isEmpty() || !removed.isEmpty()) {
            // Flushes first and clears afterwards. Without the clear, the rows
            // just deleted would still be managed, and re-saving the same keys
            // would produce an update of a row that is no longer there.
            rolePermissions.deleteAllByRoleId(roleId);

            rolePermissions.saveAll(wanted.stream()
                    .map(code -> new RolePermission(roleId, idsByCode.get(code)))
                    .toList());

            events.publishEvent(
                    new RoleEvents.PermissionsChanged(workspaceId, actor(), roleId, slug, added, removed));
        }

        return new RoleResponse(roleId, slug, name, scope, system, wanted.stream().sorted().toList());
    }

    /**
     * Refuses an edit that would leave the caller unable to edit roles again.
     *
     * <p>The narrow rule, deliberately: it applies only when the role being edited is the caller's
     * own role in this workspace. A workspace administrator who removes {@code role:manage} from
     * their own role could never put it back, and only the platform administrator could repair it.
     * An installation should not have to call them to undo a typo.
     *
     * <p>It does <strong>not</strong> attempt the global rule that some role somewhere must retain
     * the grant. That one has to count holders on every edit and would refuse a legitimate change to
     * a role nobody holds.
     *
     * <p>{@code SUPER_ADMIN} is unaffected and that is correct rather than an oversight. It edits
     * through its platform role and holds no workspace membership, so there is no "own role" here
     * and the rule does not apply. It is precisely the repair path this rule exists to avoid needing.
     */
    private void requireCallerKeepsRoleManagement(UUID workspaceId, Role role, Set<String> wanted) {
        if (wanted.contains(Permissions.ROLE_MANAGE)) {
            return;
        }

        UUID callerId = CurrentUser.find()
                .map(com.company.taskmanagementplatform.common.security.AuthenticatedUser::id)
                .orElse(null);
        if (callerId == null) {
            return;
        }

        boolean editingOwnRole = members.findByWorkspaceIdAndUserId(workspaceId, callerId)
                .map(member -> member.getRoleId().equals(role.getId()))
                .orElse(false);

        if (editingOwnRole) {
            throw new ConflictException(
                    "That would remove your own ability to edit roles in this workspace. "
                            + "Grant role management to another role first, or ask a platform administrator.");
        }
    }

    private Set<String> currentCodes(UUID roleId) {
        Map<UUID, String> codesById =
                permissions.findAll().stream().collect(Collectors.toMap(Permission::getId, Permission::getCode));

        return rolePermissions.findAllByIdRoleId(roleId).stream()
                .map(grant -> codesById.get(grant.getId().getPermissionId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static UUID actor() {
        return CurrentUser.find()
                .map(com.company.taskmanagementplatform.common.security.AuthenticatedUser::id)
                .orElse(null);
    }
}
