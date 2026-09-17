package com.company.taskmanagementplatform.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * What the production transport actually puts on the wire.
 *
 * <p>The three flows the requirements name — verification, password reset and workspace invitation —
 * each carry a single-use token, and each is useless without a working link. So the assertions here
 * are about the link and the envelope rather than about prose.
 */
@ExtendWith(MockitoExtension.class)
class SmtpMailSenderTest {

    private static final String BASE = "https://app.example.com";

    @Mock
    private JavaMailSender transport;

    private final MailProperties properties =
            new MailProperties(MailProperties.Provider.SMTP, "noreply@example.com", "Task Platform", BASE, false);

    @Test
    void verificationCarriesAWorkingLink() {
        send(new MailMessage(
                "ada@example.com", MailMessage.MailTemplate.EMAIL_VERIFICATION, Map.of("token", "tok-123")));

        SimpleMailMessage sent = captured();
        assertThat(sent.getTo()).containsExactly("ada@example.com");
        assertThat(sent.getSubject()).isEqualTo("Confirm your email address");
        assertThat(sent.getText()).contains(BASE + "/verify-email?token=tok-123");
    }

    @Test
    void passwordResetCarriesAWorkingLink() {
        send(new MailMessage(
                "ada@example.com", MailMessage.MailTemplate.PASSWORD_RESET, Map.of("token", "tok-456")));

        assertThat(captured().getText()).contains(BASE + "/reset-password?token=tok-456");
    }

    @Test
    void aPasswordChangeNoticeCarriesNoLinkAtAll() {
        // Nothing to click, so nothing to steal. It reports a change that has already
        // happened and tells the reader what to do if it was not them.
        send(new MailMessage("ada@example.com", MailMessage.MailTemplate.PASSWORD_CHANGED, Map.of()));

        SimpleMailMessage sent = captured();
        assertThat(sent.getSubject()).isEqualTo("Your password was changed");
        assertThat(sent.getText()).doesNotContain("http").doesNotContain("token");
    }

    @Test
    void theSenderCarriesTheDisplayNameWhenThereIsOne() {
        send(new MailMessage("ada@example.com", MailMessage.MailTemplate.PASSWORD_CHANGED, Map.of()));

        assertThat(captured().getFrom()).isEqualTo("Task Platform <noreply@example.com>");
    }

    @Test
    void aTransportFailureIsSwallowedRatherThanThrown() {
        // MailSender's contract: a message that cannot be delivered is a logged
        // failure, never a failed request. A registration that already committed must
        // not be reported as an error because a mail server was briefly unavailable.
        doThrow(new MailSendException("the server said no")).when(transport).send(any(SimpleMailMessage.class));

        assertThatCode(() -> new SmtpMailSender(transport, properties)
                        .send(new MailMessage(
                                "ada@example.com",
                                MailMessage.MailTemplate.EMAIL_VERIFICATION,
                                Map.of("token", "tok-123"))))
                .doesNotThrowAnyException();
    }

    /** Every template renders, so a new one cannot be added without a subject and a body. */
    @Test
    void everyTemplateRenders() {
        for (MailMessage.MailTemplate template : MailMessage.MailTemplate.values()) {
            MailMessage message =
                    new MailMessage("ada@example.com", template, Map.of("token", "t", "workspaceName", "W"));

            assertThat(MailRendering.subject(message)).isNotBlank();
            assertThat(MailRendering.body(message, MailRendering.link(message, BASE)))
                    .isNotBlank();
        }
    }

    /** The address is masked wherever it is logged, and never appears in full. */
    @Test
    void addressesAreMaskedForTheLog() {
        assertThat(MailRendering.mask("ada@example.com")).isEqualTo("a***@example.com");
        assertThat(MailRendering.mask("nonsense")).isEqualTo("(redacted)");
        assertThat(MailRendering.mask(null)).isEqualTo("(none)");
    }

    private void send(MailMessage message) {
        new SmtpMailSender(transport, properties).send(message);
    }

    private SimpleMailMessage captured() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(transport).send(sent.capture());
        List<SimpleMailMessage> all = sent.getAllValues();
        return all.get(all.size() - 1);
    }
}
