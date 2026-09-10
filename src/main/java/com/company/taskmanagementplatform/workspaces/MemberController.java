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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.workspaces.dto.ChangeMemberRoleRequest;
import com.company.taskmanagementplatform.workspaces.dto.MemberResponse;
import com.company.taskmanagementplatform.workspaces.dto.RoleResponse;

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
    private final WorkspaceAccessGuard guard;

    MemberController(MembershipService memberships, RoleQueryService roles, WorkspaceAccessGuard guard) {
        this.memberships = memberships;
        this.roles = roles;
        this.guard = guard;
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
}
