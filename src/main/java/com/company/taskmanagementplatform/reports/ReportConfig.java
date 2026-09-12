package com.company.taskmanagementplatform.reports;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The whole of the reports module's wiring, which is one properties record.
 *
 * <p>No executor, no scheduler, no listener and no cache. Phase eight reads and returns; it publishes
 * no event, consumes none, and adds no thread to the application. That is worth stating in the one
 * place a reader would look for such a thing, because every other module of this size brought at
 * least one of them.
 */
@Configuration
@EnableConfigurationProperties(ReportProperties.class)
class ReportConfig {}
