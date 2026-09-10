package com.company.taskmanagementplatform.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.error.ErrorCode;

/**
 * Answers a request that reached a protected endpoint with no credentials at all.
 *
 * <p>Distinct from a rejected token, which the filter has already answered with a code saying the
 * token was invalid or expired. This one means nothing was offered.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    RestAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        errorWriter.write(request, response, ErrorCode.UNAUTHORIZED);
    }
}
