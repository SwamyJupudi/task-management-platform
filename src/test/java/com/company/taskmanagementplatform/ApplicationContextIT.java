package com.company.taskmanagementplatform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.company.taskmanagementplatform.common.error.GlobalExceptionHandler;
import com.company.taskmanagementplatform.common.web.RequestIdFilter;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * Proves the application starts end to end: real database, migrations applied, every foundation
 * component wired. If this fails, nothing else is worth reading.
 */
class ApplicationContextIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithFoundationComponents() {
        assertThat(context.getBean(RequestIdFilter.class)).isNotNull();
        assertThat(context.getBean(GlobalExceptionHandler.class)).isNotNull();
    }

    @Test
    void postgresContainerIsRunning() {
        assertThat(POSTGRES.isRunning()).isTrue();
    }
}
