package com.company.taskmanagementplatform.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

/**
 * Which transport the configuration wires, and the two combinations it refuses.
 *
 * <p>The refusals are the point. A production deployment that silently delivers nothing cannot
 * verify an address, reset a password or complete an invitation, and because verification is
 * required to sign in, nobody but the bootstrap administrator can get in at all. Every request
 * succeeds while that is true, so nothing else in this suite would notice.
 */
class MailConfigTest {

    private final MailConfig config = new MailConfig();

    @Test
    void developmentGetsTheLoggingTransport() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");

        assertThat(config.mailSender(logging(), none(), environment)).isInstanceOf(LoggingMailSender.class);
    }

    @Test
    void productionRefusesToStartWithTheLoggingTransport() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");

        assertThatThrownBy(() -> config.mailSender(logging(), none(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivers nothing")
                .hasMessageContaining("MAIL_PROVIDER=SMTP");
    }

    @Test
    void smtpWithoutAHostIsRefused() {
        // Not a bean check: Spring Boot treats spring.mail.host as present when it is
        // set to an empty string, which is this application's default, so the absence
        // has to be read from the property itself.
        MockEnvironment environment = new MockEnvironment().withProperty("spring.mail.host", "");

        assertThatThrownBy(() -> config.mailSender(smtp("noreply@example.com"), none(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host is not set")
                .hasMessageContaining("SMTP_HOST");
    }

    @Test
    void smtpWithoutASenderIsRefused() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.mail.host", "smtp.example.com");

        assertThatThrownBy(() -> config.mailSender(smtp(""), available(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.from is not set")
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    void aConfiguredSmtpDeploymentGetsTheRealTransport() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.mail.host", "smtp.example.com");

        assertThat(config.mailSender(smtp("noreply@example.com"), available(), environment))
                .isInstanceOf(SmtpMailSender.class);
    }

    private static MailProperties logging() {
        return new MailProperties(MailProperties.Provider.LOG, "", "", "https://app.example.com", false);
    }

    private static MailProperties smtp(String from) {
        return new MailProperties(MailProperties.Provider.SMTP, from, "", "https://app.example.com", false);
    }

    /** No {@code JavaMailSender} in the context, which is what an unconfigured deployment has. */
    private static ObjectProvider<org.springframework.mail.javamail.JavaMailSender> none() {
        return new StubProvider(null);
    }

    private static ObjectProvider<org.springframework.mail.javamail.JavaMailSender> available() {
        return new StubProvider(new org.springframework.mail.javamail.JavaMailSenderImpl());
    }

    /**
     * The one method {@link MailConfig} calls.
     *
     * <p>A stub rather than a mock because {@code ObjectProvider} has a dozen default methods and
     * only {@code getIfAvailable} is used; mocking it would say less about what is required.
     */
    private record StubProvider(org.springframework.mail.javamail.JavaMailSender instance)
            implements ObjectProvider<org.springframework.mail.javamail.JavaMailSender> {

        @Override
        public org.springframework.mail.javamail.JavaMailSender getIfAvailable() {
            return instance;
        }

        @Override
        public org.springframework.mail.javamail.JavaMailSender getObject() {
            return instance;
        }

        @Override
        public org.springframework.mail.javamail.JavaMailSender getObject(Object... args) {
            return instance;
        }

        @Override
        public org.springframework.mail.javamail.JavaMailSender getIfUnique() {
            return instance;
        }
    }
}
