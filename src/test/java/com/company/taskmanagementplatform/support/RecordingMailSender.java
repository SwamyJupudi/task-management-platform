package com.company.taskmanagementplatform.support;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.company.taskmanagementplatform.common.mail.MailMessage;
import com.company.taskmanagementplatform.common.mail.MailSender;

/**
 * Captures messages instead of sending them, so a test can read the token out of one.
 *
 * <p>This is what lets verification, reset and invitation be driven from end to end with no mail
 * server anywhere near the suite. It is also why the mail port exists at all.
 */
public class RecordingMailSender implements MailSender {

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(MailMessage message) {
        sent.add(message);
    }

    public List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    /** The token from the most recent message of this kind, which is what a recipient would click. */
    public Optional<String> lastToken(MailMessage.MailTemplate template) {
        return lastMessage(template).map(message -> message.variables().get("token"));
    }

    public Optional<MailMessage> lastMessage(MailMessage.MailTemplate template) {
        return sent.stream()
                .filter(message -> message.template() == template)
                .reduce((first, second) -> second);
    }

    public Optional<MailMessage> lastMessageTo(String recipient) {
        return sent.stream()
                .filter(message -> message.recipient().equalsIgnoreCase(recipient))
                .reduce((first, second) -> second);
    }

    public long countOf(MailMessage.MailTemplate template) {
        return sent.stream().filter(message -> message.template() == template).count();
    }
}
