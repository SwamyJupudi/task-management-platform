package com.company.taskmanagementplatform.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies a mail transport, falling back to the one that only logs.
 *
 * <p>The fallback is conditional rather than profile-bound, so adding a real transport later means
 * contributing a {@link MailSender} bean and deleting nothing.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    @ConditionalOnMissingBean(MailSender.class)
    public MailSender loggingMailSender(MailProperties properties) {
        return new LoggingMailSender(properties);
    }
}
