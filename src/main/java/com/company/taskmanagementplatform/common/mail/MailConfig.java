package com.company.taskmanagementplatform.common.mail;

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
 * </ul>
 *
 * <p>The fallback stays conditional on there being no other {@link MailSender} bean, so a deployment
 * that wants a provider's own API rather than SMTP contributes one and deletes nothing. The test
 * suite relies on exactly that.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    @ConditionalOnMissingBean(MailSender.class)
    public MailSender mailSender(
            MailProperties properties, ObjectProvider<JavaMailSender> transport, Environment environment) {

        if (properties.provider() == MailProperties.Provider.SMTP) {
            return smtpSender(properties, transport, environment);
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "app.mail.provider is LOG, which writes messages to the log and delivers nothing. "
                            + "Verification, password reset and workspace invitations would all silently "
                            + "fail to arrive. Set MAIL_PROVIDER=SMTP with SMTP_HOST and MAIL_FROM.");
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
}
