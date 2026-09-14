package com.company.taskmanagementplatform.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * The endpoints a deployment platform and an API consumer depend on: health probes for the
 * orchestrator, and the generated contract for clients.
 */
class ObservabilityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReportsUpWhenTheDatabaseIsReachable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void livenessAndReadinessProbesAreExposed() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void openApiDocumentIsGenerated() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Internal Task and Project Management Platform API"));
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void theHardeningHeadersArePresentAlongsideTheFoundationOnes() throws Exception {
        // Four added in the hardening phase. The foundation four above are asserted
        // separately and on purpose: this must not become the test that quietly
        // stops noticing one of them went missing.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; "
                        + "frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("Permissions-Policy",
                        org.hamcrest.Matchers.containsString("camera=()")));
    }

    @Test
    void theDocumentationConsoleGetsThePolicyItCanRunUnder() throws Exception {
        // Swagger UI configures itself with an inline script, so the strict policy
        // would leave the console blank. The generated document it reads is JSON
        // and keeps the strict policy.
        mockMvc.perform(get("/swagger-ui/index.css"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self' 'unsafe-inline'")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'none'")));
    }

    @Test
    void onlyOneContentSecurityPolicyHeaderIsSent() throws Exception {
        // Two would be intersected by the browser rather than chosen between, which
        // is the whole reason the policy is written by one writer.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(result -> {
                    java.util.List<String> policies =
                            result.getResponse().getHeaders("Content-Security-Policy");
                    if (policies.size() != 1) {
                        throw new AssertionError("expected exactly one policy header, got " + policies);
                    }
                });
    }

    @Test
    void unknownPathReturnsTheStandardErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"));
    }

    @Test
    void aProtectedEndpointThatExistsStillRefusesAnAnonymousCaller() throws Exception {
        // The other half of the rule that lets an unmapped address answer 404. A real
        // endpoint must not start answering 404 as well, because that would turn the
        // authentication boundary into a way of asking which addresses are real.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
    }

    @Test
    void anUnknownPathUnderAProtectedPrefixIsStillAFourOhFour() throws Exception {
        // Guards the matcher itself. A path that merely starts like a real one has no
        // handler either, so it is not protected and must not claim to be.
        mockMvc.perform(get("/api/v1/workspaces/not-a-real-subresource/either"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
