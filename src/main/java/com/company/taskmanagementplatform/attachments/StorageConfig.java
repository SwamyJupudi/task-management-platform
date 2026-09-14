package com.company.taskmanagementplatform.attachments;

import java.net.URI;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Wires the {@link FileStore} the configured provider asks for, and refuses the combinations that
 * would break a requirement.
 *
 * <p>The requirements say attachments must be held in object storage and not on the application
 * server in production. Two refusals enforce that, and both happen at startup:
 *
 * <ul>
 *   <li>{@code LOCAL} under the {@code prod} profile. A fallback here would work, would pass every
 *       test, and would quietly put customer files on an ephemeral disk that the next deployment
 *       discards. An application that will not start is a problem somebody fixes in minutes; the
 *       other is one nobody notices until a restore is needed.
 *   <li>{@code S3} without a bucket or a region. Left to the SDK these surface on the first upload,
 *       which is to say in front of the first user, hours after the deployment that caused them.
 * </ul>
 *
 * <p><strong>Credentials are never read from configuration.</strong> The client is built on the
 * SDK's default provider chain, which prefers a container or instance role and falls back to the
 * environment. A key pair in {@code application.properties} would be a long-lived secret rotated
 * only by redeploying, and offering the option is how that becomes the normal way to run.
 */
@Configuration
@EnableConfigurationProperties({
    StorageProperties.class,
    AttachmentPurgeProperties.class,
    AttachmentRescanProperties.class
})
class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    FileStore fileStore(StorageProperties properties, Environment environment) {
        if (properties.provider() == StorageProperties.Provider.S3) {
            return s3Store(properties);
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "app.storage.provider is LOCAL, which stores attachments on the application server. "
                            + "The requirements forbid that in production. Set STORAGE_PROVIDER=S3 with a "
                            + "bucket and a region.");
        }

        Path root = Path.of(properties.localDirectory());
        log.info("Attachments are stored on local disk under {}. This is for development and tests only.", root);
        return new LocalFileStore(root);
    }

    /**
     * The production store, with its two settings checked before anything is built.
     *
     * <p>The client and the presigner are constructed here rather than exposed as beans of their own.
     * Nothing else in the application talks to S3, and a bean that anything could inject would invite
     * a second caller that bypasses {@link AttachmentService} and its authorization.
     */
    private FileStore s3Store(StorageProperties properties) {
        String bucket = require(properties.bucket(), "app.storage.bucket", "STORAGE_BUCKET");
        String region = require(properties.region(), "app.storage.region", "STORAGE_REGION");

        S3Client client = applyEndpoint(S3Client.builder(), properties)
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                // Path style addresses the bucket as /bucket/key rather than as a
                // subdomain, which is what every S3-compatible server but Amazon's
                // own wants, and what the test container needs.
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();

        S3Presigner presigner = applyEndpoint(S3Presigner.builder(), properties)
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();

        // The bucket and the region, never a credential and never a key.
        log.info(
                "Attachments are stored in object storage: bucket={} region={} endpoint={}",
                bucket,
                region,
                properties.hasEndpoint() ? properties.endpoint() : "default");

        return new S3FileStore(client, presigner, bucket);
    }

    private static S3ClientBuilder applyEndpoint(S3ClientBuilder builder, StorageProperties properties) {
        return properties.hasEndpoint() ? builder.endpointOverride(URI.create(properties.endpoint())) : builder;
    }

    private static S3Presigner.Builder applyEndpoint(S3Presigner.Builder builder, StorageProperties properties) {
        return properties.hasEndpoint() ? builder.endpointOverride(URI.create(properties.endpoint())) : builder;
    }

    /**
     * @throws IllegalStateException naming both the property and the environment variable, because
     *     the person reading a failed deployment log set one of the two and needs to know which
     */
    private static String require(String value, String property, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.storage.provider is S3, but " + property + " is not set. "
                    + "Set it, or " + variable + " in the environment.");
        }
        return value;
    }
}
