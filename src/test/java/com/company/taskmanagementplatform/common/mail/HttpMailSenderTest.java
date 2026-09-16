package com.company.taskmanagementplatform.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the HTTP transport actually puts on the wire.
 *
 * <p>Against a real HTTP server on a loopback port rather than a mocked client. The thing worth
 * proving is the request — its method, its authorization header, and a body the provider will
 * accept — and a mock of {@code HttpClient} would only prove that the code calls the method the test
 * told it to call.
 *
 * <p>{@code com.sun.net.httpserver} ships with the JDK, so this needs no dependency, exactly as the
 * sender itself needs none.
 */
class HttpMailSenderTest {

    private static final String BASE = "https://demo.example.com";

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void startTheProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", this::record);
        server.start();
    }

    @AfterEach
    void stopTheProvider() {
        server.stop(0);
    }

    private void record(HttpExchange exchange) throws IOException {
        lastMethod.set(exchange.getRequestMethod());
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] reply = "{\"id\":\"msg_1\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status.get(), reply.length);
        exchange.getResponseBody().write(reply);
        exchange.close();
    }

    @Test
    void postsTheMessageWithTheApiKeyAndAWorkingLink() {
        send(new MailMessage(
                "ada@example.com", MailMessage.MailTemplate.EMAIL_VERIFICATION, Map.of("token", "tok-123")));

        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer test-key");

        JsonNode body = json.readTree(lastBody.get());
        assertThat(body.get("from").asString()).isEqualTo("Demo Platform <demo@example.com>");
        assertThat(body.get("to").get(0).asString()).isEqualTo("ada@example.com");
        assertThat(body.get("subject").asString()).isEqualTo("Confirm your email address");
        assertThat(body.get("text").asString()).contains(BASE + "/verify-email?token=tok-123");
    }

    @Test
    void anInvitationNamesTheWorkspace() {
        send(new MailMessage(
                "ada@example.com",
                MailMessage.MailTemplate.WORKSPACE_INVITATION,
                Map.of("token", "tok-789", "workspaceName", "Platform Team")));

        JsonNode body = json.readTree(lastBody.get());
        assertThat(body.get("subject").asString()).isEqualTo("You have been invited to Platform Team");
        assertThat(body.get("text").asString()).contains(BASE + "/invitations/accept?token=tok-789");
    }

    @Test
    void aWorkspaceNameIsEscapedRatherThanConcatenated() {
        // The reason the body is built with Jackson. A workspace name is
        // user-supplied and reaches the subject, so a quote in one would end the
        // JSON string if this were assembled by hand.
        send(new MailMessage(
                "ada@example.com",
                MailMessage.MailTemplate.WORKSPACE_INVITATION,
                Map.of("token", "t", "workspaceName", "Ops \", \"to\": [\"attacker@example.com\"], \"x\": \"")));

        JsonNode body = json.readTree(lastBody.get());
        assertThat(body.get("to")).hasSize(1);
        assertThat(body.get("to").get(0).asString()).isEqualTo("ada@example.com");
        assertThat(body.get("subject").asString()).contains("attacker@example.com");
    }

    @Test
    void aRefusalIsSwallowedRatherThanThrown() {
        // MailSender's contract: a message that cannot be delivered is a logged
        // failure, never a failed request.
        status.set(422);

        assertThatCode(() -> send(new MailMessage(
                        "ada@example.com", MailMessage.MailTemplate.PASSWORD_RESET, Map.of("token", "t"))))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnreachableProviderIsSwallowedRatherThanThrown() {
        server.stop(0);

        assertThatCode(() -> send(new MailMessage(
                        "ada@example.com", MailMessage.MailTemplate.PASSWORD_RESET, Map.of("token", "t"))))
                .doesNotThrowAnyException();
    }

    private void send(MailMessage message) {
        HttpMailProperties http = new HttpMailProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/emails",
                "test-key",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        MailProperties mail = new MailProperties(
                MailProperties.Provider.LOG, "demo@example.com", "Demo Platform", BASE, false);

        new HttpMailSender(HttpClient.newHttpClient(), http, mail, json).send(message);
    }
}
