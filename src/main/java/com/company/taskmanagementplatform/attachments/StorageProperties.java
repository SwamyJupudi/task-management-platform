package com.company.taskmanagementplatform.attachments;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Where uploaded files go, and how large they may be.
 *
 * <p><strong>There is no credential here, and there will not be one.</strong> The S3 client is built
 * from the SDK's default provider chain, which reads a container or instance role before it reads
 * anything else. A key pair in configuration is a long-lived secret that has to be rotated by
 * redeploying, and putting one in this record would make that the easy path.
 *
 * @param provider which {@link FileStore} to wire. {@code LOCAL} is refused in production, because
 *     the requirements say files must not sit on the application server there and a quiet fallback
 *     is how that rule gets broken
 * @param localDirectory the root the local store writes under, used by development and tests
 * @param maxFileSize the application's own limit, checked after the bytes are read. The container
 *     limit in {@code spring.servlet.multipart} is the first line and answers 413; this one is the
 *     second and is what the error message quotes
 * @param downloadUrlTtl how long a presigned download URL stays valid. Short on purpose: the URL
 *     carries its own authorization, so its lifetime is how long a leaked one keeps working
 * @param bucket the bucket objects are written to. Required when the provider is {@code S3} and
 *     checked at startup, because a blank one fails on the first upload rather than on boot
 * @param region the region the bucket lives in. Required for {@code S3} for the same reason
 * @param endpoint an alternative S3 endpoint, for MinIO, R2, Spaces and the test container. Empty
 *     means Amazon's own endpoint for the region
 * @param pathStyleAccess addresses the bucket as a path rather than a subdomain. Needed by most
 *     S3-compatible servers, including the one the tests run against; Amazon's own endpoints do not
 *     want it
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @DefaultValue("LOCAL") Provider provider,
        @DefaultValue("./var/attachments") String localDirectory,
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue("5m") java.time.Duration downloadUrlTtl,
        @DefaultValue("") String bucket,
        @DefaultValue("") String region,
        @DefaultValue("") String endpoint,
        @DefaultValue("false") boolean pathStyleAccess) {

    /** The values {@code attachments.storage_provider} allows, so the column and this cannot drift. */
    public enum Provider {
        LOCAL,
        S3
    }

    /** True when an alternative endpoint was named, so the client should be pointed at it. */
    boolean hasEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
