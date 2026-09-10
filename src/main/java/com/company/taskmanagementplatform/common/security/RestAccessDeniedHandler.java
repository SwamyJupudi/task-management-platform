package com.company.taskmanagementplatform.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.error.ErrorCode;

/**
 * Answers a caller who is known but may not do what they asked.
 *
 * <p>The body says only that permission is missing. Which permission, and whether the record even
 * exists, are both withheld: naming them would turn a denial into a way of mapping the system.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorWriter errorWriter;

    RestAccessDeniedHandler(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        errorWriter.write(request, response, ErrorCode.FORBIDDEN);
    }
}
