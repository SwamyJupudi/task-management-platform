package com.company.taskmanagementplatform.common.mail;

import java.util.Map;

/**
 * One message to be delivered, described by what it is rather than by how it looks.
 *
 * <p>The body is not rendered here. A template name and its variables travel instead, so choosing a
 * delivery provider later does not mean rewriting the callers.
 *
 * @param recipient the address to deliver to
 * @param template which message this is
 * @param variables values the template needs, which may include a single-use token
 */
public record MailMessage(String recipient, MailTemplate template, Map<String, String> variables) {

    public MailMessage {
        variables = Map.copyOf(variables);
    }

    /** The set of messages the identity phase sends. */
    public enum MailTemplate {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        PASSWORD_CHANGED
    }
}
