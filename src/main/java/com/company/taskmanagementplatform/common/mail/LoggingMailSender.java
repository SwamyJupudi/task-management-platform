package com.company.taskmanagementplatform.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The transport used until a delivery provider is chosen.
 *
 * <p>It writes what would have been sent to the log, which is enough to drive verification, reset
 * and invitation from end to end with no mail server present.
 *
 * <p>The single-use token is the awkward part. Writing it to a log contradicts the logging rules,
 * and a link with the token removed is useless to a developer. The compromise is a flag that is off
 * by default and enabled only in the development and test profiles. With it off, the address is
 * masked and the token is replaced by a placeholder, so switching a real environment to this
 * transport by accident leaks nothing. A warning is emitted either way, because reaching this class
 * at all means nothing was actually delivered.
 *
 * <p>Registered by {@code MailConfig} only when no other {@link MailSender} bean exists, so adding a
 * real transport is enough to displace it.
 */
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    private final MailProperties properties;

    public LoggingMailSender(MailProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(MailMessage message) {
        String recipient = properties.logTokens() ? message.recipient() : MailRendering.mask(message.recipient());
        String link = link(message);

        log.warn(
                "No mail transport is configured; message not delivered. template={} recipient={} link={}",
                message.template(),
                recipient,
                link);
    }

    private String link(MailMessage message) {
        String path = MailRendering.pathFor(message.template());
        if (path == null) {
            return "(no link for this template)";
        }
        String token = message.variables().get("token");
        if (token == null) {
            return "(no token supplied)";
        }
        String base = properties.linkBaseUrl() == null ? "" : properties.linkBaseUrl();
        return base + path + "?token=" + (properties.logTokens() ? token : "(redacted)");
    }

}
