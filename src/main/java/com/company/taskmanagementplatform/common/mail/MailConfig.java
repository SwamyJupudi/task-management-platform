package com.company.taskmanagementplatform.common.mail;

import java.net.http.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;

import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the transport the configured provider asks for, and refuses the combinations that would
 * deliver nothing.
 *
 * <p>Two refusals, both at startup, and both modelled on {@code StorageConfig}, which makes the same
 * argument about attachments:
 *
 * <ul>
 *   <li><strong>{@code LOG} under the {@code prod} profile.</strong> That transport writes a line
 *       and delivers nothing. A production deployment running it cannot verify an address, cannot
 *       reset a password and cannot complete an invitation — and since email verification is
 *       required to sign in, nobody but the bootstrap administrator can get into the platform at
 *       all. The failure is silent: every request succeeds, every message vanishes, and the first
 *       report comes from a person who cannot finish signing up.
 *   <li><strong>{@code SMTP} without a host or a sender.</strong> Spring Boot only builds a {@code
 *       JavaMailSender} when {@code spring.mail.host} is set, so a missing host would otherwise
 *       surface as a confusing failure to inject a bean. A missing sender is worse, because it
 *       starts cleanly and is rejected by the provider at the first registration.
 *   <li><strong>{@code HTTP} without an API key or a sender.</strong> The same argument: a bearer
 *       token that is absent collects 401s from the provider, and an unrecognised sender is
 *       rejected. Asking for {@code HTTP} is asking for delivery, so neither is a downgrade to the
 *       log — a deployment that wants the log asks for {@code LOG}.
 * </ul>
 *
 * <p>The fallback stays conditional on there being no other {@link MailSender} bean, so a deployment
 * that wants a provider's own API rather than SMTP contributes one and deletes nothing. The test
 * suite relies on exactly that, and so does {@code DemoMailConfig}, which contributes the same HTTP
 * transport as a primary bean whenever a key is configured under the demo profile — that path is
 * what makes a key alone enough there, without also setting {@code MAIL_PROVIDER}.
 */
@Configuration
@EnableConfigurationProperties({MailProperties.class, HttpMailProperties.class})
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    @ConditionalOnMissingBean(MailSender.class)
    public MailSender mailSender(
            MailProperties properties,
            HttpMailProperties http,
            ObjectProvider<JavaMailSender> transport,
            JsonMapper json,
            Environment environment) {

        if (properties.provider() == MailProperties.Provider.SMTP) {
            return smtpSender(properties, transport, environment);
        }

        if (properties.provider() == MailProperties.Provider.HTTP) {
            return httpSender(properties, http, json);
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "app.mail.provider is LOG, which writes messages to the log and delivers nothing. "
                            + "Verification, password reset and workspace invitations would all silently "
                            + "fail to arrive. Set MAIL_PROVIDER=SMTP with SMTP_HOST and MAIL_FROM, or "
                            + "MAIL_PROVIDER=HTTP with MAIL_HTTP_API_KEY and MAIL_FROM.");
        }

        log.warn("No mail transport is configured; messages will be logged rather than delivered.");
        return new LoggingMailSender(properties);
    }

    private MailSender smtpSender(
            MailProperties properties, ObjectProvider<JavaMailSender> transport, Environment environment) {

        // The property rather than the bean, and that distinction matters. Boot's
        // condition treats spring.mail.host as present when it is set to an empty
        // string, which is what this application's defaults do, so a JavaMailSender
        // pointing at nowhere would exist and the bean check below would pass.
        String host = environment.getProperty("spring.mail.host", "");
        if (host.isBlank()) {
            throw new IllegalStateException("app.mail.provider is SMTP, but spring.mail.host is not set, so there is "
                    + "nothing to send through. Set SMTP_HOST, and SMTP_PORT, SMTP_USERNAME and "
                    + "SMTP_PASSWORD as the provider requires.");
        }

        JavaMailSender javaMailSender = transport.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException(
                    "app.mail.provider is SMTP and spring.mail.host is set, but Spring Boot built no "
                            + "JavaMailSender. Check that spring-boot-starter-mail is on the classpath.");
        }
        if (properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException("app.mail.provider is SMTP, but app.mail.from is not set. Most providers "
                    + "reject a message whose sender they do not recognise, and that rejection would "
                    + "otherwise arrive at the first registration. Set MAIL_FROM.");
        }

        log.info("Mail is delivered over SMTP from {}.", MailRendering.mask(properties.from()));
        return new SmtpMailSender(javaMailSender, properties);
    }

    /**
     * The same transport {@code DemoMailConfig} contributes, selected by name rather than by
     * profile, so an environment can ask for it with {@code MAIL_PROVIDER=HTTP} wherever SMTP is
     * awkward.
     *
     * <p>The key is read from the property and tested for blankness for the reason {@code
     * spring.mail.host} is: this application's defaults set {@code app.mail.http.api-key} to an
     * empty string, so "configured" has to mean non-blank rather than present.
     */
    private MailSender httpSender(MailProperties properties, HttpMailProperties http, JsonMapper json) {

        if (http.apiKey() == null || http.apiKey().isBlank()) {
            throw new IllegalStateException("app.mail.provider is HTTP, but app.mail.http.api-key is not set, so every "
                    + "send would be refused by the provider as unauthenticated. Set MAIL_HTTP_API_KEY, "
                    + "or set MAIL_PROVIDER=LOG outside production to have the links written to the log "
                    + "instead.");
        }
        if (properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException("app.mail.provider is HTTP, but app.mail.from is not set. Most providers "
                    + "reject a message whose sender they do not recognise, and that rejection would "
                    + "otherwise arrive at the first registration. Set MAIL_FROM.");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(http.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        log.info("Mail is delivered over HTTP to {} from {}.", http.url(), MailRendering.mask(properties.from()));
        return new HttpMailSender(client, http, properties, json);
    }
}
