package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The three ways storage can be configured, and the two that must not start.
 *
 * <p>A context runner rather than a full application, because every one of these assertions is about
 * {@link StorageConfig} deciding whether to hand back a bean. Booting the platform to discover that
 * would take a container and thirty seconds to learn nothing more.
 *
 * <p>These are the tests that keep the requirement honest. "Attachments must not be stored on the
 * application server in production" is enforced by a refusal at startup and by nothing else, so a
 * regression here is silent: the application would run, every other test would pass, and customer
 * files would go on an ephemeral disk.
 */
class StorageConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(StorageConfig.class));

    @Test
    void localStorageIsWiredOutsideProduction() {
        runner.withPropertyValues("app.storage.provider=LOCAL", "app.storage.local-directory=./target/config-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileStore.class))
                            .isInstanceOf(LocalFileStore.class)
                            .extracting(FileStore::provider)
                            .isEqualTo("LOCAL");
                });
    }

    @Test
    void localStorageIsRefusedInProduction() {
        runner.withPropertyValues("spring.profiles.active=prod", "app.storage.provider=LOCAL")
                .run(context -> {
                    assertThat(context).hasFailed();
                    // The message has to name the way out, not only the problem.
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("stores attachments on the application server")
                            .hasMessageContaining("STORAGE_PROVIDER=S3");
                });
    }

    @Test
    void s3WithoutABucketIsRefused() {
        runner.withPropertyValues("app.storage.provider=S3", "app.storage.region=eu-west-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("app.storage.bucket is not set")
                            .hasMessageContaining("STORAGE_BUCKET");
                });
    }

    @Test
    void s3WithoutARegionIsRefused() {
        runner.withPropertyValues("app.storage.provider=S3", "app.storage.bucket=attachments")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("app.storage.region is not set")
                            .hasMessageContaining("STORAGE_REGION");
                });
    }

    /**
     * The combination a production deployment actually runs.
     *
     * <p>No credential is configured and none is needed to build the client: the SDK resolves them
     * lazily, on the first call. That is what lets this assert the wiring without reaching AWS, and
     * it is also why a missing role is a runtime failure rather than a startup one — the two settings
     * this class does check are the ones that can be checked early.
     */
    @Test
    void s3IsWiredInProductionWhenTheBucketAndRegionAreSet() {
        runner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.storage.provider=S3",
                        "app.storage.bucket=attachments",
                        "app.storage.region=eu-west-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileStore.class))
                            .isInstanceOf(S3FileStore.class)
                            .extracting(FileStore::provider)
                            .isEqualTo("S3");
                });
    }
}
