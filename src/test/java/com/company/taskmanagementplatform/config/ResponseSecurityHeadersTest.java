package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The policy choice, which is the only decision this writer makes.
 *
 * <p>The important assertion is the last one: exactly one content security policy header leaves the
 * application. Two would be intersected by the browser rather than chosen between, which would give
 * the documentation console neither policy.
 */
class ResponseSecurityHeadersTest {

    private final ResponseSecurityHeaders writer = new ResponseSecurityHeaders();

    @Test
    void anApiResponseForbidsEverySource() {
        MockHttpServletResponse response = write("/api/v1/tasks");

        assertThat(response.getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .isEqualTo(ResponseSecurityHeaders.API_CONTENT_SECURITY_POLICY)
                .contains("default-src 'none'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    void theGeneratedDocumentIsJsonAndTakesTheStrictPolicy() {
        // The console reaches this over connect-src 'self', so it does not need
        // the looser policy of the page that fetches it.
        assertThat(write("/v3/api-docs").getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .isEqualTo(ResponseSecurityHeaders.API_CONTENT_SECURITY_POLICY);
    }

    @Test
    void theConsolePageAndItsAssetsTakeThePolicySwaggerCanRunUnder() {
        assertThat(write("/swagger-ui.html").getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .isEqualTo(ResponseSecurityHeaders.DOCS_CONTENT_SECURITY_POLICY)
                .contains("script-src 'self' 'unsafe-inline'");

        assertThat(write("/swagger-ui/index.css").getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .isEqualTo(ResponseSecurityHeaders.DOCS_CONTENT_SECURITY_POLICY);
    }

    @Test
    void theConsolePolicyStillRefusesFramingAndForeignSources() {
        String policy = ResponseSecurityHeaders.DOCS_CONTENT_SECURITY_POLICY;

        assertThat(policy).contains("frame-ancestors 'none'");
        assertThat(policy).contains("default-src 'self'");
        assertThat(policy).doesNotContain("*");
    }

    @Test
    void theOtherThreeHeadersAreTheSameOnEveryResponse() {
        for (String path : new String[] {"/api/v1/tasks", "/swagger-ui.html", "/actuator/health"}) {
            MockHttpServletResponse response = write(path);

            assertThat(response.getHeader(ResponseSecurityHeaders.PERMISSIONS_POLICY_HEADER))
                    .isEqualTo(ResponseSecurityHeaders.PERMISSIONS_POLICY)
                    .contains("camera=()")
                    .contains("geolocation=()");
            assertThat(response.getHeader(ResponseSecurityHeaders.OPENER_POLICY_HEADER))
                    .isEqualTo("same-origin");
            assertThat(response.getHeader(ResponseSecurityHeaders.RESOURCE_POLICY_HEADER))
                    .isEqualTo("same-origin");
        }
    }

    @Test
    void aContextPathDoesNotHideTheConsoleFromThePolicyChoice() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/swagger-ui.html");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        assertThat(response.getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .isEqualTo(ResponseSecurityHeaders.DOCS_CONTENT_SECURITY_POLICY);
    }

    @Test
    void exactlyOnePolicyHeaderIsWrittenEvenWhenTheWriterRunsTwice() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);
        writer.writeHeaders(request, response);

        assertThat(response.getHeaders(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .hasSize(1);
    }

    private MockHttpServletResponse write(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.writeHeaders(request, response);
        return response;
    }
}
