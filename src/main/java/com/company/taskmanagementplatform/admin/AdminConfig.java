package com.company.taskmanagementplatform.admin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The whole of the admin module's wiring, which is one properties record.
 *
 * <p>No executor, no scheduler, no listener and no cache. Phase nine reads and composes; it
 * publishes no event, consumes none, and adds no thread to the application. The writes it offers are
 * performed by the modules that own the rows, and those publish their own events.
 *
 * <p>Worth stating in the one place a reader would look for such a thing, exactly as
 * {@code ReportConfig} states it, because a module this size usually brings at least one of them.
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
class AdminConfig {}
