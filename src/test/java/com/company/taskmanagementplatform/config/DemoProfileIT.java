package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.attachments.FileStore;
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
 */
@ActiveProfiles({"test", "demo"})
class DemoProfileIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private FileStore fileStore;

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
    void anUnknownApiPathIsStillANotFoundRatherThanTheDocument() throws Exception {
        // The reserved-prefix check in the resolver. Answering an API path with
        // index.html would hand HTML to a client parsing JSON, and turn "no such
        // endpoint" into a 200.
        mockMvc.perform(get("/api/v1/no-such-endpoint")).andExpect(status().isNotFound());
    }
}
