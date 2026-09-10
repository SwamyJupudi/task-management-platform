package com.company.taskmanagementplatform.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;

/**
 * Pulls the two details a session record keeps about where it came from.
 *
 * <p>The address is taken from the request rather than from a forwarded header directly. Behind a
 * proxy that is handled once, centrally, by {@code server.forward-headers-strategy}, which is safer
 * than every call site deciding for itself whether to trust a header a client can set.
 */
final class ClientContext {

    private ClientContext() {}

    static String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    static String ipAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
