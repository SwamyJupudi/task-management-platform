package com.company.taskmanagementplatform.common.mail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.json.JsonMapper;

/**
 * A mail transport that posts to a provider's HTTP API instead of speaking SMTP.
 *
 * <p>Written for the free-tier demo, where SMTP is awkward: a platform that hosts arbitrary code
 * generally restricts outbound mail ports to keep itself off block lists, and a free instance has no
 * static address to build a sender reputation on anyway. An HTTPS call to a provider has neither
 * problem — it is an ordinary outbound request on 443.
 *
 * <p><strong>No new dependency.</strong> The JDK's own {@link HttpClient} makes the call and the
 * application's Jackson mapper builds the body. A provider SDK would bring a transitive tree, an
 * upgrade cadence and a second HTTP stack into the image for one POST.
 *
 * <p>The body is Resend's shape, which several providers accept: {@code from}, {@code to}, {@code
 * subject}, {@code text}. It is built with Jackson rather than string concatenation, which is not
 * fussiness — a workspace name is user-supplied and appears in the subject, and hand-written JSON is
 * how that becomes an injection.
 *
 * <h2>What is never logged</h2>
 *
 * <p>Not the API key, not the token, not the link, not the address in full, and <strong>not the
 * provider's response body</strong>. The last one is the non-obvious case: an error body commonly
 * echoes the request it rejected, and the request contains the link. The status code is what an
 * operator needs to tell "the provider refused it" from "the provider never answered", and it is
 * all that is written.
 *
 * <h2>Failures are logged, not thrown</h2>
 *
 * <p>{@link MailSender} requires it, and {@link SmtpMailSender} does the same. A registration that
 * has already committed must not be reported as failed because a provider was briefly unreachable;
 * the account exists, and the person can ask for another message.
 */
class HttpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(HttpMailSender.class);

    private final HttpClient client;
    private final HttpMailProperties http;
    private final MailProperties mail;
    private final JsonMapper json;

    HttpMailSender(HttpClient client, HttpMailProperties http, MailProperties mail, JsonMapper json) {
        this.client = client;
        this.http = http;
        this.mail = mail;
        this.json = json;
    }

    @Override
    public void send(MailMessage message) {
        String masked = MailRendering.mask(message.recipient());

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(http.url()))
                    .timeout(http.requestTimeout())
                    .header("Authorization", "Bearer " + http.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(message)))
                    .build();

            // discarding(), so the response body is never held in memory and cannot
            // reach a log by accident. The status is the whole of what is read.
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() / 100 == 2) {
                log.info("Mail accepted by the provider: template={} recipient={}", message.template(), masked);
            } else {
                log.error(
                        "The mail provider refused the message: template={} recipient={} status={}",
                        message.template(),
                        masked,
                        response.statusCode());
            }

        } catch (InterruptedException e) {
            // Restored rather than swallowed: something is shutting this thread down
            // and the next blocking call has to see that.
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending mail: template={} recipient={}", message.template(), masked);
        } catch (IOException | RuntimeException e) {
            // The exception's message can carry the endpoint, never the payload.
            log.error("Mail was not delivered: template={} recipient={}", message.template(), masked, e);
        }
    }

    /**
     * The request body.
     *
     * <p>The sender line is composed the way {@link SmtpMailSender} composes it, from the same two
     * settings, so a message looks the same whichever transport carried it.
     */
    private String body(MailMessage message) {
        String address = mail.from();
        String name = mail.fromName();
        String from = name == null || name.isBlank() ? address : name + " <" + address + ">";

        return json.writeValueAsString(Map.of(
                "from", from,
                "to", List.of(message.recipient()),
                "subject", MailRendering.subject(message),
                "text", MailRendering.body(message, MailRendering.link(message, mail.linkBaseUrl()))));
    }
}
