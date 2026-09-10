package com.company.taskmanagementplatform.common.mail;

/**
 * The way out of the application for anything addressed to a person.
 *
 * <p>A port, not an implementation. No delivery provider has been chosen, and nothing in the
 * identity phase should have to change when one is. Callers publish a {@link MailMessage} and know
 * nothing about transport, retries, or templating engines.
 *
 * <p>Implementations must not throw. A message that cannot be delivered is a logged failure, never a
 * failed request: a registration that succeeded and then rolled back because a mail server was down
 * would be worse for the user than one whose message arrives late.
 */
public interface MailSender {

    void send(MailMessage message);
}
