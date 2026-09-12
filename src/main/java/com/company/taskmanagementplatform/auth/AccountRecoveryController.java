package com.company.taskmanagementplatform.auth;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The two recovery actions an administrator may take on somebody else's account.
 *
 * <p>Mapped under {@code /users} but living in {@code auth}, and that split is deliberate rather
 * than untidy. Both flows are owned by this module: it holds the single-use tokens, the mail events
 * and the session revocation. Putting the endpoints on {@code UserController} would mean {@code
 * users} importing {@code auth}, and {@code auth} already imports {@code users} to ask it about
 * credentials, so the dependency would become a cycle. A controller's package has never had to match
 * its path here; {@code ActivityController} maps under {@code /workspaces} for the same reason.
 *
 * <p>Both are platform-scoped, through {@code user:update}, which no workspace role holds.
 *
 * <p><strong>There is no endpoint here that sets a password.</strong> An administrator starts a
 * recovery and the token goes to the address that owns the account. They never learn a credential
 * that opens somebody else's account, and the reset that follows revokes every session, which is the
 * behaviour somebody recovering from a compromise needs.
 */
@RestController
@RequestMapping("${app.api.base-path}/users/{userId}")
@Tag(name = "Account recovery", description = "Administrative password recovery and address verification")
class AccountRecoveryController {

    private final PasswordService passwords;
    private final RegistrationService registrations;

    AccountRecoveryController(PasswordService passwords, RegistrationService registrations) {
        this.passwords = passwords;
        this.registrations = registrations;
    }

    @PostMapping("/password-reset")
    @PreAuthorize("@perm.onPlatform('user:update')")
    @Operation(
            summary = "Start a password recovery for an account",
            description = "The single-use token is mailed to the account's own address; the caller never sees it")
    ResponseEntity<Void> startPasswordReset(@PathVariable UUID userId) {
        passwords.requestResetFor(CurrentUser.requireId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    @PreAuthorize("@perm.onPlatform('user:update')")
    @Operation(
            summary = "Send another verification message",
            description = "Refused for an address that has already been confirmed")
    ResponseEntity<Void> resendVerification(@PathVariable UUID userId) {
        registrations.resendVerificationFor(CurrentUser.requireId(), userId);
        return ResponseEntity.noContent().build();
    }
}
