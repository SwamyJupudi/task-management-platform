package com.company.taskmanagementplatform.admin;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.admin.dto.ApproveUserRequest;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.users.dto.UserResponse;
import com.company.taskmanagementplatform.workspaces.WorkspaceAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admitting a registration to a workspace.
 *
 * <h2>Why the workspace is in the path</h2>
 *
 * <p>Because it is the thing being authorised. The caller's right to approve is their right to bring
 * somebody into <em>this</em> workspace, so the identifier that decides the check and the identifier
 * that is written have to be the same one. Taking the workspace from a body field would leave two
 * values that have to be kept in step, and a mismatch would be an authorisation bug rather than a
 * validation error.
 *
 * <h2>Who may do it</h2>
 *
 * <p>{@code member:invite} in the workspace being joined. That is the permission that has always
 * meant "may bring somebody new into this workspace" — the word outlived the invitation flow, the
 * meaning did not — and the seeded roles already draw the line in the right place: a workspace
 * {@code ADMIN} holds it, {@code TEAM_LEAD} and {@code EMPLOYEE} hold only {@code member:read}. No
 * permission was added and no role's grants changed.
 *
 * <p>A platform administrator passes the same check everywhere, because a platform role satisfies a
 * workspace permission in any workspace. So {@code SUPER_ADMIN} can approve anybody into anything,
 * and a workspace administrator can approve only into the workspaces they administer — which is the
 * isolation rule the rest of the application already runs on, not a second one written here.
 *
 * <p>The guard answers 404 rather than 403 for a workspace the caller has nothing to do with, so an
 * administrator of one workspace cannot use this endpoint to discover that another exists.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/pending-users")
@Tag(name = "Member approval", description = "Admitting registrations to a workspace")
class MemberApprovalController {

    private final AccountApprovalService approvals;
    private final WorkspaceAccessGuard guard;

    MemberApprovalController(AccountApprovalService approvals, WorkspaceAccessGuard guard) {
        this.approvals = approvals;
        this.guard = guard;
    }

    /**
     * Everybody waiting, for whoever may admit them.
     *
     * <p>Not scoped to the workspace, because a registration belongs to no workspace until it is
     * approved into one — there is no tenant to scope it to. What is scoped is the right to see the
     * queue at all: only somebody who could act on it is shown it.
     */
    @GetMapping
    @Operation(
            summary = "Registrations waiting to be admitted",
            description = "Oldest first; the person who has waited longest is the one to deal with next")
    PageResponse<UserResponse> pending(
            @PathVariable UUID workspaceId, @PageableDefault(size = 20) Pageable pageable) {

        guard.requirePermission(workspaceId, Permissions.MEMBER_INVITE);
        return approvals.pending(pageable);
    }

    @PostMapping("/{userId}/approve")
    @Operation(
            summary = "Approve a waiting account into this workspace",
            description = "Grants the named role, optionally joins a project, and activates the account")
    UserResponse approve(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody ApproveUserRequest request) {

        // To change rather than to read, so an archived workspace refuses this the
        // way it refuses every other change inside it.
        guard.requirePermissionToChange(workspaceId, Permissions.MEMBER_INVITE);

        // Naming a project is a second decision and takes a second permission, so
        // an approval cannot reach a project its author could not reach directly.
        // That the project belongs to this workspace and is not archived is checked
        // by ProjectMembershipService, which owns those rules.
        if (request.projectId() != null) {
            guard.requirePermissionToChange(workspaceId, Permissions.PROJECT_MANAGE_MEMBERS);
        }

        return approvals.approve(workspaceId, userId, request, CurrentUser.requireId());
    }
}
