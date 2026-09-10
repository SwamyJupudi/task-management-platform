package com.company.taskmanagementplatform.auth;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.company.taskmanagementplatform.common.mail.MailMessage;
import com.company.taskmanagementplatform.common.mail.MailSender;

/**
 * Sends the messages the identity flows generate, once their transaction has committed.
 *
 * <p>After commit, because the alternative is worse in both directions: a verification message for a
 * registration that then rolled back would point at an account that does not exist, and a mail server
 * being slow should not fail a registration that was otherwise fine.
 *
 * <p>Transport failures are logged and swallowed. There is nothing left to roll back by this point,
 * and letting the exception escape would turn a successful request into a confusing error.
 */
@Component
class IdentityMailListener {

    private static final Logger log = LoggerFactory.getLogger(IdentityMailListener.class);

    private final MailSender mail;

    IdentityMailListener(MailSender mail) {
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onVerificationRequested(IdentityMailEvents.VerificationRequested event) {
        send(new MailMessage(
                event.email(), MailMessage.MailTemplate.EMAIL_VERIFICATION, Map.of("token", event.rawToken())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPasswordResetRequested(IdentityMailEvents.PasswordResetRequested event) {
        send(new MailMessage(
                event.email(), MailMessage.MailTemplate.PASSWORD_RESET, Map.of("token", event.rawToken())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPasswordChanged(IdentityMailEvents.PasswordChanged event) {
        send(new MailMessage(event.email(), MailMessage.MailTemplate.PASSWORD_CHANGED, Map.of()));
    }

    private void send(MailMessage message) {
        try {
            mail.send(message);
        } catch (RuntimeException e) {
            // No address and no token in the message: enough to find the request,
            // nothing that should not be in a log.
            log.error("Failed to send an identity message of type {}", message.template(), e);
        }
    }
}
