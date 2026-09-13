package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * The other half of the download endpoint: what happens when the store can sign a URL.
 *
 * <p>Every other attachment test exercises the streaming branch, because the local store the suite
 * runs on issues no URLs. That left the branch a production deployment will actually take — a 302 to
 * object storage — written but never executed. This covers it, and it does so without needing S3:
 * the endpoint's job is to notice that a URL came back and redirect to it, and a store that returns
 * one is enough to prove that. {@code S3FileStoreIT} separately proves that the URL a real provider
 * issues works when fetched.
 *
 * <p>The authorization is the point worth restating. The redirect is issued only after the same
 * check the streaming path makes, so somebody who cannot see the task cannot obtain a signed URL
 * either. The tests below assert exactly that, because a leak here would hand out a credential
 * rather than merely a response.
 */
@Import(AttachmentRedirectDownloadIT.SigningStoreConfig.class)
class AttachmentRedirectDownloadIT extends AbstractCollaborationIT {

    private static final String SIGNED = "https://objects.example.com/signed-object";

    @Test
    void aStoreThatCanSignRedirectsInsteadOfStreaming() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "notes.txt", CollaborationFixtures.TEXT, scene.adminId());

        MvcResult result = mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, SIGNED))
                .andReturn();

        // Nothing is streamed on this path, which is the whole point of it: the
        // bytes never pass through the application.
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void theStoreIsAskedForTheFilenameAndTypeTheDatabaseRecorded() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "quarterly report.txt", CollaborationFixtures.TEXT, scene.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isFound());

        // Both are what the signed URL has to carry for the download to arrive as
        // an attachment of the right type, so the endpoint must pass through what
        // was stored rather than anything the client said.
        assertThat(SigningStore.lastFilename).isEqualTo("quarterly report.txt");
        assertThat(SigningStore.lastContentType).isEqualTo("text/plain");
        assertThat(SigningStore.lastTtl).isNotNull();
    }

    @Test
    void somebodyOffTheProjectIsRefusedBeforeAnyUrlIsSigned() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "notes.txt", CollaborationFixtures.TEXT, scene.adminId());
        UUID outsider = fixtures.verifiedUser(uniqueEmail("outsider")).id();
        fixtures.addMember(scene.workspaceId(), outsider, "EMPLOYEE");

        SigningStore.lastFilename = null;

        // 404 rather than 403: a file nobody may see must not be confirmed to
        // exist. The important half is the assertion after it.
        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());

        assertThat(SigningStore.lastFilename)
                .as("a refused download must not reach the store at all")
                .isNull();
    }

    @Test
    void anAnonymousCallerIsRefusedBeforeAnyUrlIsSigned() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "notes.txt", CollaborationFixtures.TEXT, scene.adminId());

        SigningStore.lastFilename = null;

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content"))
                .andExpect(status().isUnauthorized());

        assertThat(SigningStore.lastFilename).isNull();
    }

    /**
     * A store that signs, standing in for a provider that can.
     *
     * <p>Primary rather than a replacement of the real bean, matching how the suite substitutes its
     * mail transport. It writes what it was asked for into static fields, because the assertion the
     * redirect tests need is about the arguments rather than about the result.
     */
    @TestConfiguration
    static class SigningStoreConfig {

        @Bean
        @Primary
        FileStore signingFileStore() {
            return new SigningStore();
        }
    }

    /** Delegates nothing: the redirect path never reads or writes bytes. */
    static class SigningStore implements FileStore {

        static volatile String lastFilename;
        static volatile String lastContentType;
        static volatile Duration lastTtl;

        @Override
        public String provider() {
            return StorageProperties.Provider.S3.name();
        }

        @Override
        public void put(String key, InputStream content, String contentType, long sizeBytes) {
            // Accepted and discarded. The upload path is covered by the tests that
            // run against the local store; this class exists for the download.
        }

        @Override
        public InputStream open(String key) {
            throw new AssertionError("open() must not be called when the store can sign a URL");
        }

        @Override
        public void delete(String key) {
            // Nothing calls this yet, here or anywhere else.
        }

        @Override
        public Optional<URI> presignedUrl(String key, Duration ttl, String filename, String contentType) {
            lastFilename = filename;
            lastContentType = contentType;
            lastTtl = ttl;
            return Optional.of(URI.create(SIGNED));
        }
    }
}
