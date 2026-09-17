package com.company.taskmanagementplatform.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import tools.jackson.databind.json.JsonMapper;

/**
 * Which transport the configuration wires, and the combinations it refuses.
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

        assertThat(wire(logging(), noKey(), none(), environment)).isInstanceOf(LoggingMailSender.class);
    }

    @Test
    void productionRefusesToStartWithTheLoggingTransport() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");

        assertThatThrownBy(() -> wire(logging(), noKey(), none(), environment))
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

        assertThatThrownBy(() -> wire(smtp("noreply@example.com"), noKey(), none(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host is not set")
                .hasMessageContaining("SMTP_HOST");
    }

    @Test
    void smtpWithoutASenderIsRefused() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.mail.host", "smtp.example.com");

        assertThatThrownBy(() -> wire(smtp(""), noKey(), available(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.from is not set")
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    void aConfiguredSmtpDeploymentGetsTheRealTransport() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.mail.host", "smtp.example.com");

        assertThat(wire(smtp("noreply@example.com"), noKey(), available(), environment))
                .isInstanceOf(SmtpMailSender.class);
    }

    @Test
    void httpWithoutAnApiKeyIsRefused() {
        // Blank rather than absent, for the reason the host check is: the application's
        // own defaults set app.mail.http.api-key to an empty string.
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> wire(http("noreply@example.com"), noKey(), none(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.http.api-key is not set")
                .hasMessageContaining("MAIL_HTTP_API_KEY");
    }

    @Test
    void httpWithoutASenderIsRefused() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> wire(http(""), key(), none(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.from is not set")
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    void aConfiguredHttpDeploymentGetsTheHttpTransport() {
        // No JavaMailSender and no spring.mail.host: the HTTP transport shares none of
        // SMTP's configuration, and a deployment that picked it has none of it set.
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");

        assertThat(wire(http("noreply@example.com"), key(), none(), environment)).isInstanceOf(HttpMailSender.class);
    }

    @Test
    void theHttpProviderBindsFromConfigurationAndWiresThroughSpring() {
        // The one assertion the direct calls above cannot make. They construct the
        // arguments themselves, so they would still pass with HTTP missing from the
        // enum or HttpMailProperties unregistered -- which is precisely the pair that
        // failed a deployment: MAIL_PROVIDER=HTTP would not bind at all.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailConfig.class))
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withPropertyValues(
                        "app.mail.provider=HTTP",
                        "app.mail.from=noreply@example.com",
                        "app.mail.link-base-url=https://app.example.com",
                        "app.mail.http.api-key=re_test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MailSender.class)).isInstanceOf(HttpMailSender.class);
                });
    }

    private MailSender wire(
            MailProperties mail,
            HttpMailProperties http,
            ObjectProvider<org.springframework.mail.javamail.JavaMailSender> transport,
            MockEnvironment environment) {

        return config.mailSender(mail, http, transport, JsonMapper.builder().build(), environment);
    }

    private static HttpMailProperties noKey() {
        return new HttpMailProperties("https://api.resend.com/emails", "", Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    private static HttpMailProperties key() {
        return new HttpMailProperties(
                "https://api.resend.com/emails", "re_test", Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    private static MailProperties http(String from) {
        return new MailProperties(MailProperties.Provider.HTTP, from, "", "https://app.example.com", false);
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
