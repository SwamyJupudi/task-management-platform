package com.company.taskmanagementplatform.auth;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.security.SecurityProperties;

/**
 * The one place that knows the refresh token travels in a cookie.
 *
 * <p>Isolating it is the point. The approved transport is an {@code HttpOnly}, {@code Secure},
 * {@code SameSite=Strict} cookie scoped to the authentication path, chosen because a script that
 * manages to run on the page cannot read it, which is not true of anything kept in local storage.
 * But that choice rests on the frontend being served same-site with the API, and if the deployment
 * ever separates them, {@code SameSite=Strict} stops working and the token has to move into the
 * response body.
 *
 * <p>When that day comes, this class is what changes. Nothing else in the codebase reads or writes
 * the cookie, so the swap is one file and its test rather than a search through every endpoint.
 *
 * <p>The path scoping is worth noting: the cookie is attached only to the authentication endpoints,
 * so the long-lived credential is not sent along with every ordinary request that has no use for it.
 */
@Component
public class RefreshTokenCookieCodec {

    private final SecurityProperties.Cookie properties;

    RefreshTokenCookieCodec(SecurityProperties properties) {
        this.properties = properties.cookie();
    }

    /** Reads the token out of the request, if it is there. */
    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (properties.name().equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** The header value that sets the cookie, for a freshly issued or rotated token. */
    public String issueHeader(String rawToken) {
        return buildCookie(rawToken, properties.ttl()).toString();
    }

    /**
     * The header value that removes it.
     *
     * <p>Same name, same path, empty value, zero age. A browser only replaces a cookie when the name
     * and path match, so a clearing cookie written at a different path leaves the original in place.
     */
    public String clearHeader() {
        return buildCookie("", Duration.ZERO).toString();
    }

    public String headerName() {
        return HttpHeaders.SET_COOKIE;
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path(properties.path())
                .maxAge(maxAge)
                .build();
    }
}
