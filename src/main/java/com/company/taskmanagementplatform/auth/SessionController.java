package com.company.taskmanagementplatform.auth;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.auth.dto.SessionResponse;
import com.company.taskmanagementplatform.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A person's own sessions.
 *
 * <p>Scoped to the caller at every point: the identifier comes from the security context, never from
 * the path, so there is no shape of request that reads or ends somebody else's session.
 */
@RestController
@RequestMapping("${app.api.base-path}/auth/sessions")
@Tag(name = "Sessions", description = "See and end your own sessions")
class SessionController {

    private final SessionService sessions;
    private final RefreshTokenCookieCodec cookies;

    SessionController(SessionService sessions, RefreshTokenCookieCodec cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List your live sessions")
    List<SessionResponse> list(HttpServletRequest http) {
        return sessions.list(CurrentUser.requireId(), cookies.read(http).orElse(null));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "End one of your sessions")
    ResponseEntity<Void> revoke(@PathVariable UUID sessionId) {
        sessions.revoke(CurrentUser.requireId(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
