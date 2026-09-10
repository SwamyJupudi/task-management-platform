package com.company.taskmanagementplatform.support;

import java.util.UUID;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for every integration test.
 *
 * <p>Tests run against a real PostgreSQL container rather than an in-memory substitute, so
 * migrations, constraints and types behave exactly as they will in production. The container is
 * static, so it starts once and is shared across the suite.
 *
 * <p>Without Docker the class is skipped rather than failed, which keeps a developer machine
 * without Docker usable. Continuous integration always has Docker, so nothing is silently lost
 * there.
 *
 * <p>Because the container is shared, the schema is not reset between tests. Rather than truncating
 * tables, which would couple every test to the table list, each test names its own people and
 * workspaces through {@link #uniqueEmail} and {@link #uniqueSlug}. Tests then cannot collide, and
 * they can run in any order.
 *
 * <p>The container is started here rather than through {@code @Container}, and that difference
 * matters. The JUnit extension ties a container to the lifecycle of the class that declares it, so
 * it would stop this one after each test class. Spring, meanwhile, caches an application context and
 * reuses it for the next class, and that context holds a connection pool bound to the port the
 * container had. The second class to run would then be pointed at a container that no longer exists.
 * Starting it from a static initialiser instead ties it to the JVM: one container, one port, valid
 * for as long as any cached context might still refer to it. Ryuk still removes it when the JVM
 * exits, so nothing is left behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMailConfig.class, IdentityFixtures.class})
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected static String uniqueSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
