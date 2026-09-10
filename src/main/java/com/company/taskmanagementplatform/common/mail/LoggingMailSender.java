package com.company.taskmanagementplatform.common.mail;

import java.util.Map;

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

    private static final Map<MailMessage.MailTemplate, String> LINK_PATHS = Map.of(
            MailMessage.MailTemplate.EMAIL_VERIFICATION, "/verify-email",
            MailMessage.MailTemplate.PASSWORD_RESET, "/reset-password",
            MailMessage.MailTemplate.WORKSPACE_INVITATION, "/invitations/accept");

    private final MailProperties properties;

    public LoggingMailSender(MailProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(MailMessage message) {
        String recipient = properties.logTokens() ? message.recipient() : mask(message.recipient());
        String link = link(message);

        log.warn(
                "No mail transport is configured; message not delivered. template={} recipient={} link={}",
                message.template(),
                recipient,
                link);
    }

    private String link(MailMessage message) {
        String path = LINK_PATHS.get(message.template());
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

    /** Keeps enough of an address to recognise it, not enough to reuse it. */
    private static String mask(String address) {
        int at = address.indexOf('@');
        if (at <= 0) {
            return "(redacted)";
        }
        char initial = address.charAt(0);
        return initial + "***" + address.substring(at);
    }
}
