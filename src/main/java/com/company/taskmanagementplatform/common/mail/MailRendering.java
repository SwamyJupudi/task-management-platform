package com.company.taskmanagementplatform.common.mail;

import java.util.Map;

/**
 * What each template says, and where its link points.
 *
 * <p>One home for the four paths, shared by both transports. They were private to {@link
 * LoggingMailSender} until a real one existed; a second copy is how the link in a delivered message
 * and the link in a development log start pointing at different pages.
 *
 * <p>Plain text, and deliberately so. An HTML message needs a template engine, a text alternative
 * and a set of decisions about rendering in clients nobody here can test against; every one of these
 * messages is a sentence and a link, and a sentence and a link render everywhere.
 */
final class MailRendering {

    private static final Map<MailMessage.MailTemplate, String> LINK_PATHS = Map.of(
            MailMessage.MailTemplate.EMAIL_VERIFICATION, "/verify-email",
            MailMessage.MailTemplate.PASSWORD_RESET, "/reset-password");

    private MailRendering() {}

    /** The page a template's link opens, or null for a message that carries no link. */
    static String pathFor(MailMessage.MailTemplate template) {
        return LINK_PATHS.get(template);
    }

    /** The full link for a message, or null when the template has no page or the message no token. */
    static String link(MailMessage message, String baseUrl) {
        String path = pathFor(message.template());
        String token = message.variables().get("token");
        if (path == null || token == null) {
            return null;
        }
        return (baseUrl == null ? "" : baseUrl) + path + "?token=" + token;
    }

    static String subject(MailMessage message) {
        return switch (message.template()) {
            case EMAIL_VERIFICATION -> "Confirm your email address";
            case PASSWORD_RESET -> "Reset your password";
            case PASSWORD_CHANGED -> "Your password was changed";
        };
    }

    /**
     * The body.
     *
     * <p>Every one of these says what will happen if the reader was not expecting it, because a
     * message about somebody's account that offers no explanation is indistinguishable from a
     * phishing attempt.
     */
    static String body(MailMessage message, String link) {
        return switch (message.template()) {
            case EMAIL_VERIFICATION -> """
                    Confirm your email address to finish setting up your account:

                    %s

                    The link can be used once and expires. If you did not create an account, \
                    you can ignore this message.
                    """
                    .formatted(link);
            case PASSWORD_RESET -> """
                    Somebody asked to reset the password for this account. Choose a new one here:

                    %s

                    The link can be used once and expires. If it was not you, nothing has changed \
                    and you can ignore this message.
                    """
                    .formatted(link);
            case PASSWORD_CHANGED -> """
                    The password for your account was changed, and every other session has been \
                    signed out.

                    If this was not you, reset your password immediately and contact your \
                    administrator.
                    """;
        };
    }

    /**
     * Keeps enough of an address to recognise it, not enough to reuse it.
     *
     * <p>Used by every log line either transport writes. The address is personal information and the
     * logging rules say it does not belong in a log in full.
     */
    static String mask(String address) {
        if (address == null) {
            return "(none)";
        }
        int at = address.indexOf('@');
        if (at <= 0) {
            return "(redacted)";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
