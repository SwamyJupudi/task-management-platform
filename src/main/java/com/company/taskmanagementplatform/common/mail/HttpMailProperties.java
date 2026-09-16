package com.company.taskmanagementplatform.common.mail;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The HTTP mail transport's own settings, kept apart from {@link MailProperties}.
 *
 * <p>Separate because this transport is a demo arrangement and {@link MailProperties} is the
 * application's contract. Folding an API key into the shared record would offer it to production,
 * where mail goes over SMTP and the key would be a second way to do the same thing.
 *
 * <p>The envelope is deliberately <em>not</em> here: {@code MAIL_FROM} and {@code MAIL_FROM_NAME} are
 * read from {@link MailProperties} exactly as the SMTP transport reads them, so the two agree about
 * who a message is from.
 *
 * @param url the provider's send endpoint. The default is Resend's, whose body shape this sender
 *     writes; a provider that accepts the same shape can be pointed at with no code change
 * @param apiKey the bearer token. From the environment, never from a file in this repository, and
 *     never written to a log
 * @param connectTimeout how long to wait for the connection
 * @param requestTimeout how long to wait for the whole exchange. Delivery happens on a listener
 *     thread after the causing transaction has committed, so an unbounded wait would hold that
 *     thread rather than fail a request
 */
@ConfigurationProperties(prefix = "app.mail.http")
record HttpMailProperties(
        @DefaultValue("https://api.resend.com/emails") String url,
        @DefaultValue("") String apiKey,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration requestTimeout) {}
