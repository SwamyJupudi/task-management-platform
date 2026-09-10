package com.company.taskmanagementplatform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.auth.dto.AuthTokenResponse;
import com.company.taskmanagementplatform.auth.dto.EmailRequest;
import com.company.taskmanagementplatform.auth.dto.LoginRequest;
import com.company.taskmanagementplatform.auth.dto.MeResponse;
import com.company.taskmanagementplatform.auth.dto.RegisterRequest;
import com.company.taskmanagementplatform.auth.dto.VerifyEmailRequest;
import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.users.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Registration, verification and the session endpoints.
 *
 * <p>The refresh token never appears in a body here. It is written to and read from a cookie by
 * {@link RefreshTokenCookieCodec}, which is the only class that knows the transport.
 */
@RestController
@RequestMapping("${app.api.base-path}/auth")
@Tag(name = "Authentication", description = "Registration, sign-in, and session lifecycle")
class AuthController {

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final CurrentUserService currentUser;
    private final RefreshTokenCookieCodec cookies;

    AuthController(
            RegistrationService registration,
            AuthenticationService authentication,
            CurrentUserService currentUser,
            RefreshTokenCookieCodec cookies) {
        this.registration = registration;
        this.authentication = authentication;
        this.currentUser = currentUser;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create an account",
            description = "Creates a person, not a workspace. No tokens are issued until the address is confirmed")
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(registration.register(
                request.email(), request.password(), request.firstName(), request.lastName()));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an address using the token from the message")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        registration.verify(request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Send another verification message",
            description = "Always accepted, whether or not the address is registered")
    void resendVerification(@Valid @RequestBody EmailRequest request) {
        registration.resendVerification(request.email());
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Returns an access token and sets the refresh cookie")
    ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthenticationService.Session session = authentication.login(
                request.email(), request.password(), ClientContext.userAgent(http), ClientContext.ipAddress(http));
        return withRefreshCookie(session);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Exchange the refresh cookie for a new access token",
            description = "Rotates the refresh token; presenting a used one ends the whole session")
    ResponseEntity<AuthTokenResponse> refresh(HttpServletRequest http) {
        String rawToken = cookies.read(http).orElseThrow(UnauthorizedException::tokenInvalid);
        AuthenticationService.Session session =
                authentication.refresh(rawToken, ClientContext.userAgent(http), ClientContext.ipAddress(http));
        return withRefreshCookie(session);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "End this session",
            description = "Revokes the refresh token and clears the cookie. The access token lasts until it expires")
    ResponseEntity<Void> logout(HttpServletRequest http) {
        cookies.read(http).ifPresent(authentication::logout);
        return ResponseEntity.noContent()
                .header(cookies.headerName(), cookies.clearHeader())
                .build();
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "End every session of this account")
    ResponseEntity<Void> logoutEverywhere() {
        authentication.logoutEverywhere(CurrentUser.requireId());
        return ResponseEntity.noContent()
                .header(cookies.headerName(), cookies.clearHeader())
                .build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Describe the current session",
            description = "Profile, platform role and permissions, and the workspaces the caller belongs to")
    MeResponse me() {
        return currentUser.describe(CurrentUser.requireId());
    }

    private ResponseEntity<AuthTokenResponse> withRefreshCookie(AuthenticationService.Session session) {
        return ResponseEntity.ok()
                .header(cookies.headerName(), cookies.issueHeader(session.refreshToken().rawToken()))
                .body(AuthTokenResponse.of(
                        session.accessToken().value(),
                        session.accessToken().expiresInSeconds(),
                        UserResponse.from(session.account())));
    }
}
