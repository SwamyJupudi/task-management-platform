package com.company.taskmanagementplatform.admin;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.admin.dto.ApproveUserRequest;
import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.ProjectMembershipService;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.users.UserQueryService;
import com.company.taskmanagementplatform.users.UserStatus;
import com.company.taskmanagementplatform.users.dto.UserResponse;
import com.company.taskmanagementplatform.workspaces.MembershipService;
import com.company.taskmanagementplatform.workspaces.RoleQueryService;
import com.company.taskmanagementplatform.workspaces.dto.RoleResponse;

/**
 * Turns a registration into a member of a workspace.
 *
 * <p>This is onboarding now. Somebody registers, waits, and an administrator who is already signed
 * in decides where they belong. Nothing depends on a message arriving, which is what the invitation
 * flow it replaces could never guarantee: an address that never received its link could not join,
 * and no screen in the product could say why.
 *
 * <h2>One transaction, three decisions</h2>
 *
 * <p>The status, the workspace membership and the optional project membership are written together
 * or not at all. A half-applied approval is the state worth preventing: an account marked active
 * with no membership can sign in and reach nothing, which is indistinguishable to the person from
 * not having been approved, and an account left waiting while already holding a membership would be
 * reachable by a screen that has no reason to expect it.
 *
 * <h2>Through the services, and so through their rules</h2>
 *
 * <p>{@link MembershipService} refuses a role that is not the workspace's own and refuses an
 * archived workspace; {@link ProjectMembershipService} refuses a project that is not the
 * workspace's, and refuses somebody who is not a member of it. None of that is restated here. The
 * one rule this class owns is that a workspace and a role are decided together, which the request
 * makes structural.
 */
@Service
public class AccountApprovalService {

    private static final Logger log = LoggerFactory.getLogger(AccountApprovalService.class);

    private final UserAccountService users;
    private final MembershipService memberships;
    private final RoleQueryService roles;
    private final ProjectMembershipService projectMembers;
    private final UserQueryService userQueries;

    AccountApprovalService(
            UserAccountService users,
            MembershipService memberships,
            RoleQueryService roles,
            ProjectMembershipService projectMembers,
            UserQueryService userQueries) {
        this.userQueries = userQueries;
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.projectMembers = projectMembers;
    }

    /**
     * Approves a waiting account and places it.
     *
     * <p>Refused for an account that is not waiting, and that refusal is deliberate rather than
     * defensive. Approving somebody already active would add a second workspace membership nobody
     * asked for, and approving a deactivated account would undo an administrator's decision to
     * switch it off. Both are 409s: the caller is entitled to approve in general and is being
     * refused because of the state of this account.
     */
    @Transactional
    public UserResponse approve(UUID workspaceId, UUID userId, ApproveUserRequest request, UUID actorUserId) {
        UserAccount account = users.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (account.status() != UserStatus.PENDING_APPROVAL) {
            throw new BadRequestException("That account is not waiting for approval.");
        }

        UUID roleId = roleId(workspaceId, request.roleSlug());

        // The status first, and that order is the whole of how double approval is
        // prevented. It is a conditional update -- it changes the row only while it
        // is still PENDING_APPROVAL -- so two administrators approving the same
        // registration at the same moment cannot both proceed to grant a
        // membership. The check above is the courteous answer for the ordinary
        // case; this is the one that holds under a race.
        if (!users.approve(userId, actorUserId)) {
            throw new BadRequestException("That account is not waiting for approval.");
        }

        // Whatever is refused from here -- an archived workspace, a role that is not
        // this workspace's, a project that is not either -- rolls the approval back
        // with it, so the account is still waiting rather than activated into nothing.
        memberships.addMember(workspaceId, userId, roleId, actorUserId);

        if (request.projectId() != null) {
            projectMembers.addMember(workspaceId, request.projectId(), userId, actorUserId);
        }

        log.info(
                "Account approved: userId={} workspaceId={} role={} project={}",
                userId,
                workspaceId,
                request.roleSlug(),
                request.projectId());

        return UserResponse.from(
                users.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId)));
    }

    /**
     * Everybody waiting to be admitted, oldest first.
     *
     * <p>Not filtered by workspace, because a registration belongs to none until it is approved into
     * one. The caller's right to see this list is checked against a workspace by the controller; the
     * list itself is the platform's queue.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> pending(Pageable pageable) {
        Pageable oldestFirst = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "createdAt"));

        return PageResponse.of(userQueries.search(null, UserStatus.PENDING_APPROVAL, oldestFirst), UserResponse::from);
    }

    /**
     * The workspace's own copy of a role.
     *
     * <p>Looked up rather than accepted as an identifier, so an administrator cannot be handed one
     * workspace's role for another workspace's member. {@code MembershipService} checks the same
     * thing again; this is what turns the slug the screen shows into something to check.
     */
    private UUID roleId(UUID workspaceId, String slug) {
        return roles.listWorkspaceRoles(workspaceId).stream()
                .filter(role -> slug.equalsIgnoreCase(role.slug()))
                .findFirst()
                .map(RoleResponse::id)
                .orElseThrow(() -> new BadRequestException("That role does not exist in this workspace."));
    }
}
