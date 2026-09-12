package com.company.taskmanagementplatform.workspaces;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.workspaces.dto.ChangeMemberRoleRequest;
import com.company.taskmanagementplatform.workspaces.dto.MemberResponse;
import com.company.taskmanagementplatform.workspaces.dto.ReplaceRolePermissionsRequest;
import com.company.taskmanagementplatform.workspaces.dto.RoleResponse;
import com.company.taskmanagementplatform.workspaces.dto.WorkspacePermissionsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The roster of one workspace, and the roles available inside it.
 *
 * <p>Every method starts by asking the guard, which answers 404 for a workspace the caller cannot
 * see and 403 for one they can see but may not act in. The workspace comes from the path and never
 * from a header, so it can never be inherited from ambient state.
 *
 * <p>Reads use {@code requirePermission} and writes use {@code requirePermissionToChange}, which
 * adds the rule that an archived workspace is frozen.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}")
@Tag(name = "Workspace members", description = "Membership and role assignment within a workspace")
class MemberController {

    private final MembershipService memberships;
    private final RoleQueryService roles;
    private final RoleAdminService roleAdmin;
    private final WorkspaceAccessGuard guard;

    MemberController(
            MembershipService memberships,
            RoleQueryService roles,
            RoleAdminService roleAdmin,
            WorkspaceAccessGuard guard) {
        this.memberships = memberships;
        this.roles = roles;
        this.roleAdmin = roleAdmin;
        this.guard = guard;
    }

    /**
     * What the caller may do in this workspace.
     *
     * <p>Needs no permission code, only a relationship with the workspace, which is exactly what
     * {@link WorkspaceAccessGuard#visiblePermissions} establishes: it answers 404 for a workspace
     * that does not exist and for one the caller has nothing to do with, and otherwise returns the
     * set. The notification feed is gated the same way and for the same reason.
     *
     * <p>Requiring a permission here would defeat the purpose. {@code role:read} would be the
     * obvious candidate and an employee does not hold it, so the one caller who most needs to know
     * what they may do would be the one caller unable to ask.
     *
     * <p>It discloses nothing new. Every code returned is one the caller already holds, and the only
     * way to learn it otherwise is to make the request and see whether it is refused.
     */
    @GetMapping("/me")
    @Operation(
            summary = "What you may do in this workspace",
            description = "The caller's own permission codes. Needs membership and nothing more")
    WorkspacePermissionsResponse myPermissions(@PathVariable UUID workspaceId) {
        return new WorkspacePermissionsResponse(
                workspaceId,
                guard.visiblePermissions(workspaceId).stream().sorted().toList());
    }

    @GetMapping("/members")
    @Operation(summary = "List the members of a workspace")
    PageResponse<MemberResponse> listMembers(
            @PathVariable UUID workspaceId, @PageableDefault(size = 20) Pageable pageable) {
        guard.requirePermission(workspaceId, Permissions.MEMBER_READ);
        return PageResponse.of(memberships.listMembers(workspaceId, pageable), member -> member);
    }

    @PatchMapping("/members/{userId}")
    @Operation(summary = "Change a member's role", description = "The role must belong to this workspace")
    MemberResponse changeRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeMemberRoleRequest request) {
        guard.requirePermissionToChange(workspaceId, Permissions.MEMBER_ASSIGN_ROLE);
        return memberships.changeRole(workspaceId, userId, request.roleSlug());
    }

    @DeleteMapping("/members/{userId}")
    @Operation(summary = "Remove a member from a workspace")
    ResponseEntity<Void> removeMember(@PathVariable UUID workspaceId, @PathVariable UUID userId) {
        guard.requirePermissionToChange(workspaceId, Permissions.MEMBER_REMOVE);
        memberships.removeMember(workspaceId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    @Operation(summary = "List the roles of a workspace and what each grants")
    List<RoleResponse> listRoles(@PathVariable UUID workspaceId) {
        guard.requirePermission(workspaceId, Permissions.ROLE_READ);
        return roles.listWorkspaceRoles(workspaceId);
    }

    /**
     * Replaces what a role grants. The admin panel's role editor.
     *
     * <p>Addressed by slug rather than identifier, matching the role change on a member just above,
     * which also names a slug. A slug is what a client already holds from the listing.
     *
     * <p>{@code requirePermissionToChange} rather than {@code requirePermission}, so an archived
     * workspace is frozen against role edits exactly as it is against every other change. Editing
     * the permissions of a frozen workspace is a change like any other.
     *
     * <p>{@code role:manage} has been seeded and granted to {@code ADMIN} since phase two with
     * nothing checking it. Turning it on here widens what a workspace administrator can do without
     * any grant changing, which is a behaviour change rather than a new feature and is recorded as
     * such in the README.
     */
    @PutMapping("/roles/{roleSlug}/permissions")
    @Operation(
            summary = "Replace what a role grants",
            description = "The complete set, not a delta. Codes not listed are removed")
    RoleResponse replaceRolePermissions(
            @PathVariable UUID workspaceId,
            @PathVariable String roleSlug,
            @Valid @RequestBody ReplaceRolePermissionsRequest request) {

        guard.requirePermissionToChange(workspaceId, Permissions.ROLE_MANAGE);
        return roleAdmin.replacePermissions(workspaceId, roleSlug, request.permissions());
    }
}
