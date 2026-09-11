package com.company.taskmanagementplatform.attachments;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Wires the one {@link FileStore} this phase ships, and refuses the combination that would break the
 * requirement.
 *
 * <p>The requirements say attachments must be held in cloud or object storage and not on the
 * application server in production. No provider has been chosen, so there is no object-storage
 * implementation to wire; what there is instead is a startup failure that says so plainly.
 *
 * <p>Failing at startup rather than falling back is the decision worth naming. A fallback to local
 * storage in production would work, would pass every test, and would quietly put customer files on
 * an ephemeral disk that the next deployment discards. An application that will not start is a
 * problem somebody fixes in minutes; the other is one nobody notices until a restore is needed.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    FileStore fileStore(StorageProperties properties, Environment environment) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));

        if (properties.provider() == StorageProperties.Provider.S3) {
            throw new IllegalStateException(
                    "app.storage.provider is S3, but no object-storage client is wired yet. "
                            + "Choosing the provider is recorded as open in architecture.md; "
                            + "it needs an implementation of FileStore and the dependency that goes with it.");
        }

        if (production) {
            throw new IllegalStateException(
                    "app.storage.provider is LOCAL, which stores attachments on the application server. "
                            + "The requirements forbid that in production. Configure object storage before deploying.");
        }

        Path root = Path.of(properties.localDirectory());
        log.info("Attachments are stored on local disk under {}. This is for development and tests only.", root);
        return new LocalFileStore(root);
    }
}
