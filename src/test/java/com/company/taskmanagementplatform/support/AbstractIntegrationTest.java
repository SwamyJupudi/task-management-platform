package com.company.taskmanagementplatform.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
}
