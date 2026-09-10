package com.company.taskmanagementplatform.support;

import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.security.AccessTokenService;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.MembershipService;
import com.company.taskmanagementplatform.workspaces.PlatformRoleService;
import com.company.taskmanagementplatform.workspaces.RoleQueryService;
import com.company.taskmanagementplatform.workspaces.WorkspaceProvisioningService;
import com.company.taskmanagementplatform.workspaces.dto.RoleResponse;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * Builds the people, workspaces and memberships a test needs, through the same services the
 * application uses.
 *
 * <p>Through the services rather than by inserting rows. Setting up state with raw SQL is how a suite
 * ends up asserting against a shape the application would never actually produce.
 */
@TestComponent
public class IdentityFixtures {

    public static final String PASSWORD = "correct-horse-battery";

    private final UserAccountService users;
    private final WorkspaceProvisioningService workspaces;
    private final MembershipService memberships;
    private final RoleQueryService roles;
    private final PlatformRoleService platformRoles;
    private final AccessTokenService accessTokens;

    IdentityFixtures(
            UserAccountService users,
            WorkspaceProvisioningService workspaces,
            MembershipService memberships,
            RoleQueryService roles,
            PlatformRoleService platformRoles,
            AccessTokenService accessTokens) {
        this.users = users;
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.roles = roles;
        this.platformRoles = platformRoles;
        this.accessTokens = accessTokens;
    }

    /** An account that has registered but not yet confirmed its address. Cannot sign in. */
    @Transactional
    public UserAccount pendingUser(String email) {
        return users.register(email, PASSWORD, "Test", "Person");
    }

    /** An ordinary account that can sign in. */
    @Transactional
    public UserAccount verifiedUser(String email) {
        UserAccount account = users.register(email, PASSWORD, "Test", "Person");
        users.markEmailVerified(account.id());
        return users.findById(account.id()).orElseThrow();
    }

    /** An account holding the platform role, with no workspace membership anywhere. */
    @Transactional
    public UserAccount superAdmin(String email) {
        UserAccount account = verifiedUser(email);
        platformRoles.assignSuperAdmin(account.id());
        return users.findById(account.id()).orElseThrow();
    }

    @Transactional
    public WorkspaceResponse workspace(String name, String slug, UUID creatorUserId) {
        return workspaces.create(name, slug, creatorUserId);
    }

    @Transactional
    public void addMember(UUID workspaceId, UUID userId, String roleSlug) {
        memberships.addMember(workspaceId, userId, roleId(workspaceId, roleSlug), null);
    }

    public UUID roleId(UUID workspaceId, String roleSlug) {
        return roles.listWorkspaceRoles(workspaceId).stream()
                .filter(role -> role.slug().equals(roleSlug))
                .map(RoleResponse::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No role " + roleSlug + " in that workspace"));
    }

    /** A bearer header for an account, so a test does not have to sign in to exercise something else. */
    public String bearer(UUID userId) {
        return "Bearer " + accessTokens.issue(userId).value();
    }
}
