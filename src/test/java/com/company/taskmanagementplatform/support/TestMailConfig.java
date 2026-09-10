package com.company.taskmanagementplatform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.company.taskmanagementplatform.common.mail.MailSender;

/**
 * Puts the recording transport in front of the logging one for the whole suite.
 *
 * <p>Marked primary rather than relying on the conditional in {@code MailConfig}. That condition is
 * evaluated in registration order, which is not something a test should have to reason about; being
 * primary settles it whichever way the ordering falls.
 */
@TestConfiguration
public class TestMailConfig {

    @Bean
    @Primary
    public MailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
