package com.company.taskmanagementplatform.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.auth.dto.MeResponse;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.PermissionResolver;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.users.dto.UserResponse;
import com.company.taskmanagementplatform.workspaces.MembershipService;
import com.company.taskmanagementplatform.workspaces.RoleQueryService;

/**
 * Assembles the description of the current session that the interface needs to draw itself.
 *
 * <p>One call rather than four. Without it the frontend would fetch the profile, the memberships, the
 * platform role and the platform permissions separately, on every page load, which is the sort of
 * thing that quietly becomes the slowest request in the application.
 */
@Service
public class CurrentUserService {

    private final UserAccountService users;
    private final MembershipService memberships;
    private final RoleQueryService roles;
    private final PermissionResolver permissions;

    CurrentUserService(
            UserAccountService users,
            MembershipService memberships,
            RoleQueryService roles,
            PermissionResolver permissions) {
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public MeResponse describe(UUID userId) {
        UserAccount account =
                users.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        return new MeResponse(
                UserResponse.from(account),
                roles.roleSlug(account.platformRoleId()).orElse(null),
                List.copyOf(permissions.resolveForPlatform(userId)),
                memberships.membershipsOf(userId));
    }
}
