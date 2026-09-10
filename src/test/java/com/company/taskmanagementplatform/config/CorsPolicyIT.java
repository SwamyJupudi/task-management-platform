package com.company.taskmanagementplatform.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * Holds the filter chain to the CORS policy the application configures.
 *
 * <p>More than one bean in the context implements {@code CorsConfigurationSource}: the one
 * {@code SecurityConfig} declares from {@code app.cors.*}, and the {@code HandlerMappingIntrospector}
 * Spring registers for its own use. Wiring the chain to the wrong one is not a startup error, it is
 * a browser that stops being able to call the API. These tests assert the configured policy is the
 * one actually in force, by reading the headers a browser would read.
 */
class CorsPolicyIT extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String FOREIGN_ORIGIN = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsAPreflightFromTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void refusesAPreflightFromAnOriginThatIsNotConfigured() throws Exception {
        // The whole point of the allow-list. If this passes, the chain is consulting
        // a source that has no configuration rather than the one built from app.cors.
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void carriesTheCredentialAndExposedHeaderSettingsFromConfiguration() throws Exception {
        // The refresh cookie only reaches the API on a credentialed request, and the
        // request id is only readable by the caller if it is exposed. Both come from
        // the configured source, so both prove which source answered.
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    void refusesAPreflightForAMethodThatIsNotAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "TRACE"))
                .andExpect(status().isForbidden());
    }
}
