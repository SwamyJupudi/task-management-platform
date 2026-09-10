package com.company.taskmanagementplatform.workspaces;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.common.security.TokenHasher;
import com.company.taskmanagementplatform.common.util.Emails;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.dto.AcceptInvitationRequest;
import com.company.taskmanagementplatform.workspaces.dto.InvitationPreviewResponse;
import com.company.taskmanagementplatform.workspaces.dto.InvitationResponse;

/**
 * Invitations: issuing them, showing one to whoever holds the link, and redeeming it.
 *
 * <p>An invitation names an address rather than an account, which is what lets somebody be invited
 * before they have registered without creating a placeholder account that would need a status of its
 * own and would appear in every list.
 */
@Service
public class InvitationService {

    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceRepository workspaces;
    private final RoleRepository roles;
    private final MembershipService memberships;
    private final UserAccountService users;
    private final TokenHasher tokens;
    private final ApplicationEventPublisher events;
    private final SecurityProperties.Tokens tokenProperties;
    private final Clock clock;

    InvitationService(
            WorkspaceInvitationRepository invitations,
            WorkspaceRepository workspaces,
            RoleRepository roles,
            MembershipService memberships,
            UserAccountService users,
            TokenHasher tokens,
            ApplicationEventPublisher events,
            SecurityProperties securityProperties,
            Clock clock) {
        this.invitations = invitations;
        this.workspaces = workspaces;
        this.roles = roles;
        this.memberships = memberships;
        this.users = users;
        this.tokens = tokens;
        this.events = events;
        this.tokenProperties = securityProperties.tokens();
        this.clock = clock;
    }

    /**
     * @param roleSlug the role to invite them into, or null to use the workspace's default role
     */
    @Transactional
    public InvitationResponse invite(UUID workspaceId, String email, String roleSlug, UUID inviterUserId) {
        String normalized = Emails.normalize(email);

        Workspace workspace = workspaces.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace", workspaceId));

        Role role = resolveRole(workspace, roleSlug);

        users.findByEmail(normalized).ifPresent(account -> {
            if (memberships.isMember(workspaceId, account.id())) {
                throw new ConflictException("That person is already a member of this workspace.");
            }
        });

        // A second live invitation to the same address would leave two valid tokens
        // and a partial unique index that refuses the write. Superseding is the
        // useful behaviour: re-inviting somebody should send them a fresh link.
        invitations
                .findByWorkspaceIdAndEmailAndStatus(workspaceId, normalized, InvitationStatus.PENDING)
                .ifPresent(WorkspaceInvitation::revoke);
        invitations.flush();

        String rawToken = tokens.generate();
        Instant expiresAt = clock.instant().plus(tokenProperties.invitationTtl());

        WorkspaceInvitation invitation = invitations.save(WorkspaceInvitation.issue(
                workspaceId, normalized, role.getId(), tokens.hash(rawToken), expiresAt, inviterUserId));

        events.publishEvent(new WorkspaceInvitationIssuedEvent(normalized, workspace.getName(), rawToken));
        return toResponse(invitation, role);
    }

    /**
     * What the holder of a link is shown before accepting.
     *
     * <p>Readable without signing in, so it says only the workspace name, the address it was sent to
     * and whether that address already has an account. Nothing about the workspace's people or work.
     */
    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String rawToken) {
        WorkspaceInvitation invitation = requireRedeemable(rawToken);
        Workspace workspace = workspaces.findByIdAndDeletedAtIsNull(invitation.getWorkspaceId())
                .orElseThrow(() -> UnauthorizedException.tokenInvalid());
        Role role = roles.findById(invitation.getRoleId()).orElseThrow(UnauthorizedException::tokenInvalid);

        return new InvitationPreviewResponse(
                workspace.getName(),
                invitation.getEmail(),
                role.getSlug(),
                users.existsByEmail(invitation.getEmail()));
    }

    /**
     * Redeems an invitation, creating the account first if there is not one.
     *
     * @param currentUserId the signed-in caller, or null if the request was anonymous
     */
    @Transactional
    public UUID accept(AcceptInvitationRequest request, UUID currentUserId) {
        WorkspaceInvitation invitation = requireRedeemable(request.token());
        Optional<UserAccount> existing = users.findByEmail(invitation.getEmail());

        UUID userId = existing.isPresent()
                ? requireCallerIsInvited(existing.get(), currentUserId)
                : createInvitedAccount(invitation, request);

        memberships.addMember(
                invitation.getWorkspaceId(), userId, invitation.getRoleId(), invitation.getInvitedByUserId());
        invitation.accept(userId, clock.instant());
        return invitation.getWorkspaceId();
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID invitationId) {
        WorkspaceInvitation invitation = invitations
                .findById(invitationId)
                .filter(candidate -> candidate.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> ResourceNotFoundException.of("Invitation", invitationId));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("That invitation is no longer outstanding.");
        }
        invitation.revoke();
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> list(UUID workspaceId, Pageable pageable) {
        return invitations
                .findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                .map(invitation -> toResponse(invitation, roles.findById(invitation.getRoleId()).orElse(null)));
    }

    /**
     * An account already exists for the invited address, so the caller has to prove they are it.
     *
     * <p>Without this, holding the link would be enough to add somebody else's account to a
     * workspace.
     */
    private UUID requireCallerIsInvited(UserAccount invited, UUID currentUserId) {
        if (currentUserId == null || !currentUserId.equals(invited.id())) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED, "Please sign in as the invited account to accept this invitation.");
        }
        return invited.id();
    }

    /**
     * Creates the account named by the invitation, already verified.
     *
     * <p>Verified because the token went to that address and nowhere else, so redeeming it proves
     * exactly what a verification message would prove. Sending one anyway would be asking somebody to
     * confirm something they have just confirmed.
     */
    private UUID createInvitedAccount(WorkspaceInvitation invitation, AcceptInvitationRequest request) {
        if (isBlank(request.password()) || isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw new BadRequestException("A password and name are needed to create your account.");
        }
        return users.register(
                        invitation.getEmail(), request.password(), request.firstName(), request.lastName(), true)
                .id();
    }

    /**
     * The role named by the request, or the workspace's default when none was named.
     *
     * <p>The default is a setting rather than a constant, so an administrator who wants every new
     * person to arrive as a team lead can say so once instead of on every invitation. A workspace
     * always has one from the moment it is created, so the last branch is a guard against a row
     * edited by hand rather than a case the application produces.
     */
    private Role resolveRole(Workspace workspace, String roleSlug) {
        if (roleSlug != null && !roleSlug.isBlank()) {
            return roles.findByWorkspaceIdAndSlug(workspace.getId(), roleSlug)
                    .orElseThrow(() -> new BadRequestException("That role does not exist in this workspace."));
        }

        UUID defaultRoleId = workspace.getDefaultRoleId();
        if (defaultRoleId == null) {
            throw new BadRequestException("This workspace has no default role, so the invitation must name one.");
        }
        return roles.findByIdAndWorkspaceId(defaultRoleId, workspace.getId())
                .orElseThrow(() ->
                        new BadRequestException("This workspace has no default role, so the invitation must name one."));
    }

    private WorkspaceInvitation requireRedeemable(String rawToken) {
        WorkspaceInvitation invitation = invitations
                .findByTokenHash(tokens.hash(rawToken))
                .orElseThrow(UnauthorizedException::tokenInvalid);

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw UnauthorizedException.tokenInvalid();
        }
        if (!invitation.getExpiresAt().isAfter(clock.instant())) {
            // Settled on the way past rather than by a sweep, so nothing depends on
            // a scheduler having run.
            invitation.markExpired();
            throw UnauthorizedException.tokenExpired();
        }
        return invitation;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static InvitationResponse toResponse(WorkspaceInvitation invitation, Role role) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getWorkspaceId(),
                invitation.getEmail(),
                role == null ? null : role.getSlug(),
                invitation.getStatus().name(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt());
    }
}
