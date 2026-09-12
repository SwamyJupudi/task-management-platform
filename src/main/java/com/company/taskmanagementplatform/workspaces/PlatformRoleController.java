package com.company.taskmanagementplatform.workspaces;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.users.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Granting and revoking the platform administrator role.
 *
 * <p>Until this phase the role could only be granted by the startup bootstrap, so an installation
 * could not promote a second platform administrator without a redeploy. These are the two highest-
 * privilege endpoints in the platform, and three things about them are deliberate.
 *
 * <p><strong>Neither takes a body.</strong> There is one platform role, {@link PlatformRoleService}
 * takes no role identifier, and neither does the HTTP surface in front of it. A caller cannot name
 * the wrong role because it cannot name a role at all. The database refuses the same thing
 * independently, through a foreign key on {@code (platform_role_id, platform_role_scope)} that only
 * a platform-scoped role can satisfy.
 *
 * <p><strong>Their own permission.</strong> {@code platform_role:assign} rather than {@code
 * user:update}, so a future custom platform role can be given account administration without also
 * being given the ability to mint its own peers. Editing a surname and creating an administrator are
 * not the same capability.
 *
 * <p><strong>Mapped under {@code /users} but living here</strong>, because this module owns the role
 * table. {@code users} owns the column and writes it, but only through {@link
 * UserAccountService#assignPlatformRole}, which this service is the sole ordinary caller of.
 */
@RestController
@RequestMapping("${app.api.base-path}/users/{userId}/platform-role")
@Tag(name = "Platform role", description = "Granting and revoking the platform administrator role")
class PlatformRoleController {

    private final PlatformRoleService platformRoles;
    private final UserAccountService users;

    PlatformRoleController(PlatformRoleService platformRoles, UserAccountService users) {
        this.platformRoles = platformRoles;
        this.users = users;
    }

    @PutMapping
    @PreAuthorize("@perm.onPlatform('platform_role:assign')")
    @Operation(
            summary = "Grant the platform administrator role",
            description = "Takes no body: there is one platform role and it cannot be named")
    UserResponse grant(@PathVariable UUID userId) {
        platformRoles.assignSuperAdmin(userId);
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    /**
     * Takes the role away.
     *
     * <p>Refused for the caller's own account, and for the last holder. Both refusals sit in {@code
     * UserAccountService} so they hold however the column is written, and both answer 409: the
     * caller is entitled to do this in general and is being refused because of the state of the
     * world.
     */
    @DeleteMapping
    @PreAuthorize("@perm.onPlatform('platform_role:assign')")
    @Operation(
            summary = "Revoke the platform administrator role",
            description = "Refused for your own account, and for the last remaining administrator")
    ResponseEntity<Void> revoke(@PathVariable UUID userId) {
        platformRoles.revokeSuperAdmin(userId);
        return ResponseEntity.noContent().build();
    }
}
