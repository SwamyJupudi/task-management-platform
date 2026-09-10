package com.company.taskmanagementplatform.common.security;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.company.taskmanagementplatform.common.error.ErrorCode;

/**
 * Turns a bearer token into an authenticated caller, or into the standard error body.
 *
 * <p>Four things have to hold, and each is checked here rather than at any endpoint, so no controller
 * can forget one.
 *
 * <ol>
 *   <li>The token verifies: signature, expiry, issuer and audience.
 *   <li>The subject still exists and the account is usable, which is what makes a deactivation take
 *       effect on the very next request.
 *   <li>The token was issued after the password last changed, which is what ends other sessions when
 *       somebody changes or resets a password.
 *   <li>Nothing about the failure reaches the caller beyond the code, and the detail goes to the log
 *       against the same request id.
 * </ol>
 *
 * <p>A request with no token is passed along untouched. Whether that is allowed is the filter chain's
 * decision, not this filter's, and conflating the two is how endpoints end up accidentally public.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;
    private final AccountStatusProvider accountStatusProvider;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(
            AccessTokenService accessTokenService,
            AccountStatusProvider accountStatusProvider,
            SecurityErrorWriter errorWriter) {
        this.accessTokenService = accessTokenService;
        this.accountStatusProvider = accountStatusProvider;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String tokenValue = header.substring(BEARER_PREFIX.length()).trim();
        if (tokenValue.isEmpty()) {
            reject(request, response, ErrorCode.TOKEN_INVALID, "empty bearer token");
            return;
        }

        Jwt jwt;
        try {
            jwt = accessTokenService.parse(tokenValue);
        } catch (JwtException e) {
            // The library reports expiry through the same exception type as a bad
            // signature, so the message is the only thing separating them. Getting
            // this wrong only costs a client one wasted refresh attempt, which is
            // why it is allowed to be a heuristic.
            boolean expired = e.getMessage() != null && e.getMessage().contains("Jwt expired");
            reject(request, response, expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.TOKEN_INVALID, e.getMessage());
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            reject(request, response, ErrorCode.TOKEN_INVALID, "subject is not a user id");
            return;
        }

        Optional<AccountStatusProvider.AccountState> state = accountStatusProvider.findAccountState(userId);
        if (state.isEmpty() || !state.get().usable()) {
            reject(request, response, ErrorCode.ACCOUNT_INACTIVE, "account is not usable");
            return;
        }

        if (issuedBeforePasswordChange(jwt, state.get())) {
            reject(request, response, ErrorCode.TOKEN_EXPIRED, "token predates the current password");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean issuedBeforePasswordChange(Jwt jwt, AccountStatusProvider.AccountState state) {
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null || state.passwordChangedAt() == null) {
            return false;
        }
        // A JWT timestamp has one-second resolution, so the stored moment is
        // truncated before the comparison. Without that, a token minted in the
        // same second as the change it followed would be rejected.
        return issuedAt.isBefore(state.passwordChangedAt().truncatedTo(ChronoUnit.SECONDS));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String reason)
            throws IOException {
        SecurityContextHolder.clearContext();
        // The reason is for us. The caller gets the code and its safe message.
        log.warn("Rejected bearer token: code={} method={} path={} reason={}",
                code.name(), request.getMethod(), request.getRequestURI(), reason);
        errorWriter.write(request, response, code);
    }
}
