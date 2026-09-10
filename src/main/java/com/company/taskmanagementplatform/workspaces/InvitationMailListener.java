package com.company.taskmanagementplatform.workspaces;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.company.taskmanagementplatform.common.mail.MailMessage;
import com.company.taskmanagementplatform.common.mail.MailSender;

/**
 * Sends the invitation message, after the invitation is safely committed.
 *
 * <p>After commit rather than inside the transaction, because the two failures are not equally bad. A
 * message sent for an invitation that then rolled back would point at nothing. An invitation recorded
 * whose message failed can be re-sent.
 *
 * <p>A transport failure is swallowed on purpose. Letting it escape here would do nothing useful: the
 * transaction has already committed, so there is nothing left to undo, and an exception would only
 * surface as a confusing error on a request that in fact succeeded.
 */
@Component
class InvitationMailListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationMailListener.class);

    private final MailSender mail;

    InvitationMailListener(MailSender mail) {
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onInvitationIssued(WorkspaceInvitationIssuedEvent event) {
        try {
            mail.send(new MailMessage(
                    event.email(),
                    MailMessage.MailTemplate.WORKSPACE_INVITATION,
                    Map.of("token", event.rawToken(), "workspaceName", event.workspaceName())));
        } catch (RuntimeException e) {
            // No address, no token, no workspace name. Enough to find the request.
            log.error("Failed to send a workspace invitation message", e);
        }
    }
}
