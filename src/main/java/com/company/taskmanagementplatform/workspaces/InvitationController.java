package com.company.taskmanagementplatform.workspaces;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.workspaces.dto.AcceptInvitationRequest;
import com.company.taskmanagementplatform.workspaces.dto.InvitationPreviewResponse;
import com.company.taskmanagementplatform.workspaces.dto.InvitationResponse;
import com.company.taskmanagementplatform.workspaces.dto.InviteMemberRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Issuing invitations inside a workspace, and redeeming one from outside it.
 *
 * <p>The two halves sit at different addresses for a reason. Issuing is workspace administration and
 * is scoped to the workspace path. Redeeming is done by somebody who may have no account at all, and
 * cannot be nested under a workspace they are not yet allowed to know exists.
 */
@RestController
@Tag(name = "Invitations", description = "Inviting people to a workspace and accepting an invitation")
class InvitationController {

    private final InvitationService invitations;
    private final WorkspaceAccessGuard guard;

    InvitationController(InvitationService invitations, WorkspaceAccessGuard guard) {
        this.invitations = invitations;
        this.guard = guard;
    }

    @PostMapping("${app.api.base-path}/workspaces/{workspaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Invite an address to a workspace",
            description = "Any outstanding invitation to the same address is superseded")
    InvitationResponse invite(@PathVariable UUID workspaceId, @Valid @RequestBody InviteMemberRequest request) {
        guard.requirePermission(workspaceId, Permissions.MEMBER_INVITE);
        return invitations.invite(workspaceId, request.email(), request.roleSlug(), CurrentUser.requireId());
    }

    @GetMapping("${app.api.base-path}/workspaces/{workspaceId}/invitations")
    @Operation(summary = "List the invitations of a workspace")
    PageResponse<InvitationResponse> list(
            @PathVariable UUID workspaceId, @PageableDefault(size = 20) Pageable pageable) {
        guard.requirePermission(workspaceId, Permissions.MEMBER_READ);
        return PageResponse.of(invitations.list(workspaceId, pageable), invitation -> invitation);
    }

    @DeleteMapping("${app.api.base-path}/workspaces/{workspaceId}/invitations/{invitationId}")
    @Operation(summary = "Withdraw an outstanding invitation")
    ResponseEntity<Void> revoke(@PathVariable UUID workspaceId, @PathVariable UUID invitationId) {
        guard.requirePermission(workspaceId, Permissions.MEMBER_INVITE);
        invitations.revoke(workspaceId, invitationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * What a link shows before it is accepted. Public, because the holder may have no account yet.
     *
     * <p>The token is a query parameter here and nowhere else in the API. That is a deliberate,
     * bounded exception: the page has to be reachable from a link in a message. It returns the
     * workspace name and nothing that describes the workspace's people or work.
     */
    @GetMapping("${app.api.base-path}/invitations")
    @Operation(summary = "Preview an invitation", description = "Public; requires only the token")
    InvitationPreviewResponse preview(@RequestParam String token) {
        return invitations.preview(token);
    }

    /**
     * Redeems an invitation.
     *
     * <p>Public, but not unauthenticated in every case. If the invited address already has an
     * account, the caller must be signed in as it; otherwise holding the link would be enough to add
     * somebody else's account to a workspace.
     */
    @PostMapping("${app.api.base-path}/invitations/accept")
    @Operation(summary = "Accept an invitation", description = "Creates the account when there is not one yet")
    Map<String, UUID> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        UUID callerId = CurrentUser.find()
                .map(com.company.taskmanagementplatform.common.security.AuthenticatedUser::id)
                .orElse(null);
        return Map.of("workspaceId", invitations.accept(request, callerId));
    }
}
