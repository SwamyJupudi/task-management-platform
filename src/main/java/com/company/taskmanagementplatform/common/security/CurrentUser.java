package com.company.taskmanagementplatform.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.error.UnauthorizedException;

/** Reads the caller out of the security context. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user ? Optional.of(user) : Optional.empty();
    }

    /** For code that may only run authenticated, where absence is a wiring mistake, not a denial. */
    public static AuthenticatedUser require() {
        return find().orElseThrow(() ->
                new UnauthorizedException(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()));
    }

    public static UUID requireId() {
        return require().id();
    }
}
