package com.company.taskmanagementplatform.users;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.users.dto.AdminUpdateProfileRequest;
import com.company.taskmanagementplatform.users.dto.UpdateProfileRequest;
import com.company.taskmanagementplatform.users.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Account administration, and the one endpoint a person uses on themselves.
 *
 * <p>Every rule here is checked platform-wide rather than inside a workspace. Administering one
 * workspace must not become a way to reach the whole installation, so a workspace role never
 * satisfies these.
 */
@RestController
@RequestMapping("${app.api.base-path}/users")
@Tag(name = "Users", description = "Account directory and administration")
class UserController {

    private final UserAccountService accounts;
    private final UserQueryService queries;

    UserController(UserAccountService accounts, UserQueryService queries) {
        this.accounts = accounts;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("@perm.onPlatform('user:read')")
    @Operation(summary = "List accounts", description = "Paged, searchable directory of accounts")
    PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean locked,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(queries.search(search, status, locked, pageable), UserResponse::from);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@perm.onPlatform('user:read') or @perm.isSelf(#userId)")
    @Operation(summary = "Fetch one account")
    UserResponse get(@PathVariable UUID userId) {
        return accounts.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update your own profile")
    UserResponse updateOwnProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(
                accounts.updateProfile(CurrentUser.requireId(), request.firstName(), request.lastName()));
    }

    /**
     * The administrative edit of somebody else's profile.
     *
     * <p>Mapped after {@code /me} so the literal path wins, and routed to a different service method
     * so that a person renaming themselves is never recorded as an administrative action.
     *
     * <p>{@code user:update} has been in the permission catalog since phase two with nothing checking
     * it. This is the endpoint it was put there for. It is granted to no workspace role, so it is
     * reachable through a platform role alone, which is deliberate: renaming somebody who may also
     * work in three other workspaces is platform administration.
     */
    @PatchMapping("/{userId}")
    @PreAuthorize("@perm.onPlatform('user:update')")
    @Operation(
            summary = "Edit an account's profile",
            description = "Name only. An address is an account's identity and is not editable here")
    UserResponse updateProfile(@PathVariable UUID userId, @Valid @RequestBody AdminUpdateProfileRequest request) {
        return UserResponse.from(
                accounts.updateProfileOf(CurrentUser.requireId(), userId, request.firstName(), request.lastName()));
    }

    /**
     * Clears an automatic lockout, so somebody does not have to wait it out.
     *
     * <p>Idempotent. Unlocking an account that is not locked succeeds and writes no audit row,
     * because nothing happened.
     */
    @PostMapping("/{userId}/unlock")
    @PreAuthorize("@perm.onPlatform('user:update')")
    @Operation(summary = "Clear a sign-in lockout", description = "Idempotent; an unlocked account is unchanged")
    UserResponse unlock(@PathVariable UUID userId) {
        return UserResponse.from(accounts.unlock(CurrentUser.requireId(), userId));
    }

    @PostMapping("/{userId}/deactivate")
    @PreAuthorize("@perm.onPlatform('user:deactivate')")
    @Operation(summary = "Switch an account off", description = "Sessions end at the account's next request")
    UserResponse deactivate(@PathVariable UUID userId) {
        return UserResponse.from(accounts.deactivate(userId));
    }

    @PostMapping("/{userId}/activate")
    @PreAuthorize("@perm.onPlatform('user:activate')")
    @Operation(
            summary = "Switch an account back on",
            description = "An account that never confirmed its address returns to awaiting verification")
    UserResponse activate(@PathVariable UUID userId) {
        return UserResponse.from(accounts.activate(userId));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("@perm.onPlatform('user:delete')")
    @Operation(summary = "Remove an account", description = "Soft deletion; the row is retained but invisible")
    ResponseEntity<Void> delete(@PathVariable UUID userId) {
        accounts.softDelete(userId);
        return ResponseEntity.noContent().build();
    }
}
