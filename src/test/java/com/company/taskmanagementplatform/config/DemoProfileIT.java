package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.attachments.FileStore;
import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * That the demo profile starts, and that nothing it relaxes is a security control.
 *
 * <p>The demo profile exists so that the platform can be shown on a free Render account: one web
 * service, one Postgres instance, and no room for object storage, a malware scanner or Redis. It
 * relaxes three configurations that {@code prod} refuses to start without, and it does so by not
 * being {@code prod} — every one of those guards tests for that profile by name, so nothing in them
 * changed.
 *
 * <p>Which is exactly why this test is worth having. A properties file nobody loads is a file that
 * is wrong the first time somebody deploys it, and the deployment in question is a live
 * demonstration. This loads it.
 *
 * <p>{@code @ActiveProfiles} names {@code test} as well, because the datasource comes from the
 * container the base class starts. The demo profile wins for the keys both define, which is what
 * makes this an honest test of it.
 *
 * <p><strong>What this does not cover.</strong> The single-page fallback in {@code
 * SpaResourceConfig} needs a built bundle in {@code classpath:/static/}, which only
 * {@code Dockerfile.demo} produces; there is nothing to serve in a Maven test run, so the assertions
 * here stop at the configuration being loadable. What is asserted instead is the part that would
 * actually be dangerous if it were wrong: that serving a bundle from this application has not opened
 * the API to anonymous callers.
 *
 * <p>The content security policy <em>is</em> covered, and needs no bundle to be: the header is written
 * for every response whatever its status, so the 404 this returns without one carries the same policy
 * the document would. That gap is why the demo shipped a blank page once already - the strict policy
 * refused the bundle's own script and stylesheet, and nothing here noticed.
 */
@ActiveProfiles({"test", "demo"})
class DemoProfileIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private FileStore fileStore;

    @Autowired
    private SecurityProperties security;

    @Test
    void theProfileLoadsAndWiresTheDemoStore() {
        // LOCAL is refused under prod and is the default here. Reaching this line
        // at all means the context refreshed, so the properties file parses and
        // every placeholder in it resolved.
        assertThat(fileStore.getClass().getSimpleName()).isEqualTo("LocalFileStore");
    }

    @Test
    void theSinglePageFallbackIsRegisteredOnlyForThisProfile() {
        assertThat(context.getBeanNamesForType(SpaResourceConfig.class))
                .as("SpaResourceConfig is demo-only and must not reach production")
                .hasSize(1);
    }

    @Test
    void theApiStillRefusesAnAnonymousCaller() throws Exception {
        // The whole point of the resolver approach. A forwarding controller would
        // have been a mapped endpoint that answers anonymously; a resource handler
        // adds no mapping, so the security chain is exactly what it was.
        mockMvc.perform(get("/api/v1/workspaces")).andExpect(status().isUnauthorized());
    }

    @Test
    void theDocumentGetsThePolicyTheBundleCanRunUnder() throws Exception {
        // default-src 'none' here is what made the deployed demo a blank page: it
        // refused /assets/index-*.js and /assets/index-*.css, which are the bundle's
        // own files on its own origin.
        mockMvc.perform(get("/"))
                .andExpect(header().string(
                        ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER,
                        ResponseSecurityHeaders.APP_CONTENT_SECURITY_POLICY));

        mockMvc.perform(get("/assets/index-abc123.js"))
                .andExpect(header().string(
                        ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER,
                        ResponseSecurityHeaders.APP_CONTENT_SECURITY_POLICY));
    }

    @Test
    void thisProfileDoesNotRequireEmailVerificationToSignIn() {
        // The relaxation this profile exists to make, asserted against the loaded
        // properties rather than a literal: a demo whose mail may be going nowhere
        // must not register an account and then refuse the sign-in that follows.
        // Every other profile leaves it on, which application.properties defaults.
        assertThat(security.requireEmailVerification())
                .as("the demo profile relaxes email verification; no other profile does")
                .isFalse();
    }

    @Test
    void theApiKeepsTheStrictPolicyUnderThisProfileToo() throws Exception {
        // Serving a document from this process must not loosen the policy on JSON,
        // and an attachment download is served under the API path as well.
        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(header().string(
                        ResponseSecurityHeaders.CONTENT_SECURITY_POLICY_HEADER,
                        ResponseSecurityHeaders.API_CONTENT_SECURITY_POLICY));
    }

    @Test
    void anUnknownApiPathIsStillANotFoundRatherThanTheDocument() throws Exception {
        // The reserved-prefix check in the resolver. Answering an API path with
        // index.html would hand HTML to a client parsing JSON, and turn "no such
        // endpoint" into a 200.
        mockMvc.perform(get("/api/v1/no-such-endpoint")).andExpect(status().isNotFound());
    }
}
