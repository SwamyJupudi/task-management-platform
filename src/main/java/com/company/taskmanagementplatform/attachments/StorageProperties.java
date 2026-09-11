package com.company.taskmanagementplatform.attachments;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Where uploaded files go, and how large they may be.
 *
 * @param provider which {@link FileStore} to wire. {@code LOCAL} is refused in production, because
 *     the requirements say files must not sit on the application server there and a quiet fallback
 *     is how that rule gets broken
 * @param localDirectory the root the local store writes under, used by development and tests
 * @param maxFileSize the application's own limit, checked after the bytes are read. The container
 *     limit in {@code spring.servlet.multipart} is the first line and answers 413; this one is the
 *     second and is what the error message quotes
 * @param downloadUrlTtl how long a presigned download URL stays valid, once a provider that can
 *     issue one is wired
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @DefaultValue("LOCAL") Provider provider,
        @DefaultValue("./var/attachments") String localDirectory,
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue("5m") java.time.Duration downloadUrlTtl) {

    /** The values {@code attachments.storage_provider} allows, so the column and this cannot drift. */
    public enum Provider {
        LOCAL,
        S3
    }
}
