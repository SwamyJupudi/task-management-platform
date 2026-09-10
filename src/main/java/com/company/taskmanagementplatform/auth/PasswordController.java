package com.company.taskmanagementplatform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.auth.dto.AuthTokenResponse;
import com.company.taskmanagementplatform.auth.dto.ChangePasswordRequest;
import com.company.taskmanagementplatform.auth.dto.EmailRequest;
import com.company.taskmanagementplatform.auth.dto.ResetPasswordRequest;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.users.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Forgetting, resetting and changing a password. */
@RestController
@RequestMapping("${app.api.base-path}/auth/password")
@Tag(name = "Passwords", description = "Recovery and change")
class PasswordController {

    private final PasswordService passwords;
    private final AuthenticationService authentication;
    private final RefreshTokenCookieCodec cookies;

    PasswordController(
            PasswordService passwords, AuthenticationService authentication, RefreshTokenCookieCodec cookies) {
        this.passwords = passwords;
        this.authentication = authentication;
        this.cookies = cookies;
    }

    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Request a reset message",
            description = "Always accepted, so the response cannot be used to test whether an address is registered")
    void forgot(@Valid @RequestBody EmailRequest request) {
        passwords.requestReset(request.email());
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Set a new password using a reset token",
            description = "Ends every session, since the person may be recovering a compromised account")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwords.reset(request.token(), request.newPassword());
        return ResponseEntity.ok()
                .header(cookies.headerName(), cookies.clearHeader())
                .build();
    }

    /**
     * Changes the password of the signed-in caller.
     *
     * <p>Every session is revoked, including this one, and a fresh pair is issued immediately so the
     * caller is not signed out of the browser they are sitting in front of. Other sessions are gone.
     */
    @PostMapping("/change")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change your password", description = "Ends your other sessions and renews this one")
    ResponseEntity<AuthTokenResponse> change(
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {

        java.util.UUID userId = CurrentUser.requireId();
        passwords.change(userId, request.currentPassword(), request.newPassword());

        AuthenticationService.Session session =
                authentication.reissue(userId, ClientContext.userAgent(http), ClientContext.ipAddress(http));

        return ResponseEntity.ok()
                .header(cookies.headerName(), cookies.issueHeader(session.refreshToken().rawToken()))
                .body(AuthTokenResponse.of(
                        session.accessToken().value(),
                        session.accessToken().expiresInSeconds(),
                        UserResponse.from(session.account())));
    }
}
