package com.company.taskmanagementplatform.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.error.ApiErrorResponse;
import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.web.RequestIdFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the standard error body from inside the filter chain.
 *
 * <p>This exists because of a gap that is easy to miss. The {@code @RestControllerAdvice} only sees
 * exceptions raised once a request has reached the dispatcher, so an authentication failure in a
 * filter would otherwise produce a bare container error page with a different shape, different
 * fields and no request id. One body shape, two places that produce it, and an integration test that
 * holds them to the same contract.
 */
@Component
public class SecurityErrorWriter {

    private final JsonMapper jsonMapper;

    SecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (code.status().value() == HttpServletResponse.SC_UNAUTHORIZED) {
            // Says how to authenticate without hinting at why this attempt failed.
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        ApiErrorResponse body = ApiErrorResponse.of(
                code, code.defaultMessage(), request.getRequestURI(), RequestIdFilter.currentRequestId(request));
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
