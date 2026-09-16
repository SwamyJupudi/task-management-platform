package com.company.taskmanagementplatform.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which transport delivers a message, and what the message says about itself.
 *
 * <p><strong>There is no SMTP host, port or credential here, and there will not be one.</strong>
 * Spring Boot already binds {@code spring.mail.*} and builds a {@code JavaMailSender} from it, so
 * adding a second set of the same settings would mean two places to look and one of them wrong. This
 * record holds only what the application itself decides: which transport to use, and the envelope
 * and links the messages carry.
 *
 * @param provider which {@link MailSender} to wire. {@code LOG} is refused in production, for the
 *     reason {@code StorageProvider.LOCAL} is: a deployment that silently delivers nothing cannot
 *     verify an address, reset a password or complete an invitation, and nobody finds out until a
 *     real person is locked out of their own onboarding
 * @param from the envelope sender. Required when the provider is {@code SMTP} and checked at
 *     startup, because most providers reject a message with an unaccepted sender and that rejection
 *     would otherwise arrive at the first registration
 * @param fromName an optional display name shown beside the address
 * @param linkBaseUrl where the links in a message point, normally the frontend origin
 * @param logTokens whether the development transport may write a single-use token to the log. False
 *     everywhere except development and test, because the logging rules forbid it. See {@link
 *     LoggingMailSender}. The SMTP transport ignores it and never logs a token under any setting
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        @DefaultValue("LOG") Provider provider,
        @DefaultValue("") String from,
        @DefaultValue("") String fromName,
        String linkBaseUrl,
        boolean logTokens) {

    /** The transports this application ships. */
    public enum Provider {
        /** Writes what would have been sent to the log. Development and tests only. */
        LOG,
        /** Delivers over SMTP, through the {@code JavaMailSender} Spring Boot configures. */
        SMTP
    }
}
