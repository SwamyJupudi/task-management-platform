package com.company.taskmanagementplatform.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The production transport: SMTP, through Spring's {@link JavaMailSender}.
 *
 * <p>Spring Boot builds the {@code JavaMailSender} from {@code spring.mail.*}, so the host, port,
 * credentials and TLS settings arrive from the environment and appear nowhere in this repository.
 * {@code MailConfig} is what decides that this implementation is the one in use, and what refuses to
 * start a production deployment that has not configured it.
 *
 * <h2>What is never logged</h2>
 *
 * <p>Not the token, not the link that contains it, and not the address in full. A single-use token
 * in a log is a credential in a log: anybody who can read the log can verify somebody else's
 * address, redeem their invitation, or reset their password. The log line here carries the template,
 * a masked address, and whether it was accepted by the server — which is what an operator needs to
 * answer "was it sent" and nothing more.
 *
 * <p>The body is not logged either, since for three of the four templates the body <em>is</em> the
 * link.
 *
 * <h2>Failures are logged, not thrown</h2>
 *
 * <p>{@link MailSender} says implementations must not throw, and this one does not. A registration
 * that succeeded should not be reported as failed because a mail server was briefly unavailable; the
 * account exists, and the person can ask for another message. The exception is logged with its cause
 * and without the message's contents.
 *
 * <p>There is no retry and no queue. Both are real improvements and both need durable state to be
 * worth anything — a retry that lives in this process disappears with it. Resending is already a
 * feature the application offers, so the honest behaviour is to log the failure and let somebody ask
 * again.
 */
class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender transport;
    private final MailProperties properties;

    SmtpMailSender(JavaMailSender transport, MailProperties properties) {
        this.transport = transport;
        this.properties = properties;
    }

    @Override
    public void send(MailMessage message) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(from());
        email.setTo(message.recipient());
        email.setSubject(MailRendering.subject(message));
        email.setText(MailRendering.body(message, MailRendering.link(message, properties.linkBaseUrl())));

        try {
            transport.send(email);
            log.info(
                    "Mail accepted by the server: template={} recipient={}",
                    message.template(),
                    MailRendering.mask(message.recipient()));
        } catch (MailException e) {
            // The exception carries the server's reply, which may quote the envelope
            // but never the body, so this cannot leak the token.
            log.error(
                    "Mail was not delivered: template={} recipient={}",
                    message.template(),
                    MailRendering.mask(message.recipient()),
                    e);
        }
    }

    /**
     * The envelope sender.
     *
     * <p>A display name is optional and, when set, is prefixed in the usual form. Anything else about
     * the envelope — reply-to, return-path, DKIM — belongs to the mail provider rather than here.
     */
    private String from() {
        String address = properties.from();
        String name = properties.fromName();
        return name == null || name.isBlank() ? address : name + " <" + address + ">";
    }
}
