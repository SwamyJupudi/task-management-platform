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
 *
 * <p>The application-policy cases are the regression. {@code default-src 'none'} on the demo's own
 * document refused the bundle's script and stylesheet, and a blank page with four console errors is
 * all anybody saw.
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

    @Test
    void anApiOnlyDeploymentStillRefusesEverySourceOutsideTheConsole() {
        // Production serves the bundle from its own container, so nothing here is a
        // document and the strict policy is unchanged for every path but the console.
        for (String path : new String[] {"/", "/assets/index-abc123.js", "/favicon.svg", "/projects/xyz"}) {
            assertThat(write(path).getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                    .as("api-only deployment, path %s", path)
                    .isEqualTo(ResponseSecurityHeaders.API_CONTENT_SECURITY_POLICY);
        }
    }

    @Test
    void theBundleAndItsAssetsGetThePolicyTheSinglePageApplicationCanRunUnder() {
        // The document, the two assets the console reported blocked, and a client-side
        // route, which is reached directly on a reload and is served the document.
        for (String path : new String[] {
            "/", "/assets/index-abc123.js", "/assets/index-abc123.css", "/favicon.svg", "/projects/xyz"
        }) {
            assertThat(writeSpa(path).getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                    .as("spa deployment, path %s", path)
                    .isEqualTo(ResponseSecurityHeaders.APP_CONTENT_SECURITY_POLICY);
        }
    }

    @Test
    void theApiKeepsTheStrictPolicyEvenWhenTheBundleIsServedHere() {
        // A policy written for a document has no business on JSON, and an attachment
        // download is served under the API path too.
        for (String path : new String[] {"/api/v1/tasks", "/actuator/health", "/v3/api-docs"}) {
            assertThat(writeSpa(path).getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                    .as("spa deployment, api path %s", path)
                    .isEqualTo(ResponseSecurityHeaders.API_CONTENT_SECURITY_POLICY);
        }
    }

    @Test
    void theConsoleStillWinsWhenTheBundleIsServedHere() {
        assertThat(writeSpa("/swagger-ui.html").getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .isEqualTo(ResponseSecurityHeaders.DOCS_CONTENT_SECURITY_POLICY);
    }

    @Test
    void theApplicationPolicyPermitsWhatTheBundleNeedsAndNothingForeign() {
        String policy = ResponseSecurityHeaders.APP_CONTENT_SECURITY_POLICY;

        // Everything the browser reported blocked, now permitted from this origin.
        assertThat(policy).contains("script-src 'self'");
        assertThat(policy).contains("style-src 'self' 'unsafe-inline'");
        assertThat(policy).contains("img-src 'self' data:");
        assertThat(policy).contains("font-src 'self' data:");
        assertThat(policy).contains("connect-src 'self'");

        // Still a policy. No foreign origin, no framing, and -- the one that matters
        // most -- no inline script, which the built index.html does not need.
        assertThat(policy).doesNotContain("*");
        assertThat(policy).doesNotContain("script-src 'self' 'unsafe-inline'");
        assertThat(policy).contains("object-src 'none'");
        assertThat(policy).contains("frame-ancestors 'none'");
    }

    @Test
    void aContextPathDoesNotHideTheApiFromTheApplicationPolicy() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/v1/tasks");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ResponseSecurityHeaders(true).writeHeaders(request, response);

        assertThat(response.getHeader(ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER))
                .isEqualTo(ResponseSecurityHeaders.API_CONTENT_SECURITY_POLICY);
    }

    private MockHttpServletResponse writeSpa(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ResponseSecurityHeaders(true).writeHeaders(request, response);
        return response;
    }

    private MockHttpServletResponse write(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writer.writeHeaders(request, response);
        return response;
    }
}
