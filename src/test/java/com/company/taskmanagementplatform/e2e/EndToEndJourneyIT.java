package com.company.taskmanagementplatform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.company.taskmanagementplatform.common.mail.MailSender;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.RecordingMailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The end-to-end suite the delivery phase owes: one working day, start to finish, through the API
 * the browser actually calls.
 *
 * <p>{@code docs/testing.md} names the journey — sign in, create a project, add a member, create a
 * task, assign it, complete it — and this walks exactly that, then keeps going far enough to prove
 * the consequences: that the audit trail recorded it, that the right people were told, that the
 * reports moved, and that signing out ends the session.
 *
 * <h2>Why this exists when ninety other integration tests already pass</h2>
 *
 * <p>Every other test in this suite sets its world up through {@code IdentityFixtures} or {@code
 * TaskFixtures} and then exercises one endpoint. That is the right shape for testing a rule, and it
 * is the wrong shape for answering "does the product work", because the fixtures are a second way of
 * creating the same state and they skip the steps a person cannot skip. Nothing else in the suite
 * would notice if the invitation mail stopped carrying a token, if a newly invited account could not
 * sign in, or if the access token minted by {@code /auth/login} were rejected by the next request.
 *
 * <p>So the only fixture used here is the one that cannot be avoided: the platform administrator,
 * who exists because {@code SuperAdminBootstrap} creates them from the environment on first start.
 * There is no endpoint that makes the first one, deliberately. Everything after that — every
 * account, every token, every row — is created over HTTP by the person the journey says creates it,
 * carrying the bearer token that person was actually issued.
 *
 * <h2>Why the methods are ordered, when nothing else in the suite is</h2>
 *
 * <p>This is one story, and the steps depend on each other: there is no task to complete until
 * somebody has raised one. Written as independent tests, each would have to rebuild the whole
 * preceding journey, and the class would test the setup ten times over and the journey once.
 *
 * <p>The cost is honest and worth naming: a failure early on fails the steps after it too. The first
 * failure in report order is the real one, and each step is named so that it says which part of the
 * day broke.
 *
 * <h2>What this does not cover</h2>
 *
 * <p>MockMvc runs the real filter chain, the real security, the real controllers and services
 * against real PostgreSQL. It does not run TLS, the edge proxy, the built frontend bundle or a
 * browser. That half is the deployment's own smoke test in {@code .github/workflows/deploy.yml},
 * which runs against the public hostname after every release, and the two are deliberately split
 * that way: this one proves the product's behaviour, that one proves the deployment's plumbing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndJourneyIT extends AbstractIntegrationTest {

    /** Long enough for a loaded machine, short enough that a genuine break is not a ten-minute wait. */
    private static final Duration PATIENCE = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private MailSender mailSender;

    private RecordingMailSender mail;

    // The cast of the journey. Ada runs the workspace, Grace does the work, and
    // neither has an account when the day starts.
    private final String platformEmail = uniqueEmail("platform");
    private final String adaEmail = uniqueEmail("ada");
    private final String graceEmail = uniqueEmail("grace");
    private final String workspaceSlug = uniqueSlug("journey");
    private final String projectKey = "JRN" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);

    private String platformToken;
    private String adaToken;
    private String graceToken;
    private Cookie graceRefreshCookie;

    private UUID adaId;
    private UUID graceId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID taskId;
    private String taskKey;

    /**
     * The one piece of state no endpoint can produce.
     *
     * <p>A platform administrator is bootstrapped from {@code SUPER_ADMIN_EMAIL} and {@code
     * SUPER_ADMIN_PASSWORD} at startup, because a platform with nobody able to administer it cannot
     * be administered into existence. This mirrors that, and nothing else here is shortcut.
     */
    @BeforeAll
    void bootstrapThePlatformAdministrator() {
        mail = (RecordingMailSender) mailSender;
        mail.clear();
        fixtures.superAdmin(platformEmail);
    }

    @Test
    @Order(1)
    void thePlatformAdministratorSignsIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", platformEmail, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                // The refresh token travels in an http-only cookie and nowhere else,
                // which is what keeps it out of reach of a script on the page.
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn();

        platformToken = "Bearer " + field(result, "accessToken");

        // The token just minted is accepted by the next request, which is the whole
        // contract between sign-in and everything after it.
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformRole").value("SUPER_ADMIN"));
    }

    @Test
    @Order(2)
    void theyCreateAWorkspaceForTheTeam() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Delivery Journey", "slug", workspaceSlug))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(workspaceSlug))
                // Creating it seeds its roles in the same transaction, and the fallback
                // for an invitation that names none is set from that moment.
                .andExpect(jsonPath("$.defaultRoleSlug").value("EMPLOYEE"))
                .andReturn();

        workspaceId = uuid(result, "id");
    }

    @Test
    @Order(3)
    void twoPeopleAreInvited() throws Exception {
        // Neither address has an account. That is the ordinary case for a new
        // workspace and the one a fixture would quietly skip.
        invite(adaEmail, "ADMIN");
        invite(graceEmail, "EMPLOYEE");

        assertThat(mail.lastMessageTo(adaEmail)).isPresent();
        assertThat(mail.lastMessageTo(graceEmail)).isPresent();

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @Order(4)
    void theyRedeemTheLinksAndTheirAccountsAreCreated() throws Exception {
        SignedInSession ada = redeem(adaEmail, "Ada", "Lovelace");
        SignedInSession grace = redeem(graceEmail, "Grace", "Hopper");

        adaId = ada.userId();
        adaToken = ada.header();
        graceId = grace.userId();
        graceToken = grace.header();
        graceRefreshCookie = grace.refreshCookie();

        // Each of them is in the workspace holding the role they were invited to, and
        // the interface reads exactly this to decide what to render.
        assertThat(roleIn(adaToken, workspaceId)).isEqualTo("ADMIN");
        assertThat(roleIn(graceToken, workspaceId)).isEqualTo("EMPLOYEE");
    }

    @Test
    @Order(5)
    void theAdministratorCreatesAProjectAndStartsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header(HttpHeaders.AUTHORIZATION, adaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "key", projectKey,
                                "name", "Delivery Journey Project",
                                "description", "The work the end-to-end suite walks through.",
                                "ownerUserId", adaId.toString(),
                                "priority", "HIGH"))))
                .andExpect(status().isCreated())
                // Always PLANNING, whatever the request said, and the named owner joins it.
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.ownerUserId").value(adaId.toString()))
                .andReturn();

        projectId = uuid(result, "id");

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(6)
    void theEmployeeIsAddedToTheProject() throws Exception {
        // Being in the workspace is not being on the project. The second is what
        // makes somebody assignable, and it is a deliberate step rather than a
        // consequence of the first.
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, adaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", graceId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(graceId.toString()));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.userId == '" + graceId + "')]").exists());
    }

    @Test
    @Order(7)
    void aTaskIsRaisedAndGivenToTheEmployee() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId
                        + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, adaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "title", "Ship the delivery phase",
                                "description", "Everything the deployment needs, written down.",
                                "priority", "HIGH",
                                "dueDate", LocalDate.now().plusDays(7).toString(),
                                "estimatedMinutes", 480))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                // The first task of a project is its number one, and the key everybody
                // says out loud is composed from the project key and that number.
                .andExpect(jsonPath("$.taskNumber").value(1))
                .andExpect(jsonPath("$.key").value(projectKey + "-1"))
                .andExpect(jsonPath("$.reporterUserId").value(adaId.toString()))
                .andReturn();

        taskId = uuid(created, "id");
        taskKey = field(created, "key");

        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/tasks/" + taskId + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, adaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("assigneeUserId", graceId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeUserId").value(graceId.toString()));

        // Being given work is the one notification the requirements name by itself.
        // It is written after the transaction commits, on the notification module's
        // own thread, so it is waited for rather than asserted immediately.
        eventually(() -> assertThat(notificationTypesFor(graceToken)).contains("task.assigned"));
    }

    @Test
    @Order(8)
    void theEmployeeFindsItUnderTheirOwnWork() throws Exception {
        // Read as Grace, not as an administrator. What she can see is decided by the
        // projects she is on, and this is the query "My Tasks" actually issues.
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, graceToken)
                        .param("assigneeUserId", graceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(taskId.toString()))
                .andExpect(jsonPath("$.content[0].key").value(taskKey));
    }

    @Test
    @Order(9)
    void theEmployeeStartsTheWorkAndSaysSo() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/tasks/" + taskId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, graceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/tasks/" + taskId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, graceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("body", "Started on this. The compose file is the last piece."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorUserId").value(graceId.toString()));

        // Ada raised the task, so she is its reporter and hears about both.
        eventually(() -> assertThat(notificationTypesFor(adaToken))
                .contains("task.status_changed", "comment.created"));
    }

    @Test
    @Order(10)
    void theEmployeeCompletesIt() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/tasks/" + taskId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, graceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "DONE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                // Stamped by the move rather than by the caller, which is what makes
                // "when was this finished" answerable at all.
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    @Order(11)
    void theAuditTrailHasTheWholeDay() throws Exception {
        // The requirements ask for an audit trail, and this is the question it exists
        // to answer: what happened in this workspace today, and who did it. Every row
        // here was written by the request that caused it, by a listener nothing in the
        // journey above knows about.
        eventually(() -> assertThat(workspaceActivityActions())
                .contains(
                        "project.created",
                        "project.status_changed",
                        "task.created",
                        "task.assigned",
                        "task.status_changed",
                        "comment.created"));
    }

    @Test
    @Order(12)
    void theReportsMoved() throws Exception {
        // One task, finished, in a project Ada owns. The progress figure is derived
        // from the tasks rather than stored, so this is the end of the chain that
        // started with somebody dragging a card.
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/reports/projects")
                        .header(HttpHeaders.AUTHORIZATION, adaToken)
                        .param("ownerUserId", adaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.content[0].totalTasks").value(1))
                .andExpect(jsonPath("$.content[0].doneTasks").value(1))
                .andExpect(jsonPath("$.content[0].progress").value(100));

        // And the same fact from the other end: Grace's own dashboard, which takes no
        // user identifier at all because there is no way to render somebody else's.
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/dashboard/me")
                        .header(HttpHeaders.AUTHORIZATION, graceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myTaskCounts.total").value(1))
                .andExpect(jsonPath("$.overdueCount").value(0));
    }

    @Test
    @Order(13)
    void signingOutEndsTheSession() throws Exception {
        // 204 and an expiry cookie: there is nothing to say back, and the
        // Set-Cookie clearing the refresh token is the whole of the response.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, graceToken)
                        .cookie(graceRefreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        // The cookie she still holds is now worth nothing, which is the only thing
        // "signed out" can mean when the access token is short-lived and stateless.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(graceRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // --- the steps, in the words of the journey -------------------------------

    private void invite(String email, String roleSlug) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "roleSlug", roleSlug))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING"))
                // Only the hash is kept, so there is nothing to leak even to the
                // administrator who sent it.
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    /**
     * Opens the link, creates the account behind it, and signs in as it.
     *
     * <p>The preview is fetched with no bearer token on purpose: somebody who has never signed in has
     * to be able to see what they are being asked to join.
     */
    private SignedInSession redeem(String email, String firstName, String lastName) throws Exception {
        String token = mail.lastMessageTo(email)
                .map(message -> message.variables().get("token"))
                .orElseThrow(() -> new AssertionError("No invitation was sent to " + email));

        mockMvc.perform(get("/api/v1/invitations").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.accountExists").value(false));

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "token", token,
                                "password", IdentityFixtures.PASSWORD,
                                "firstName", firstName,
                                "lastName", lastName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()));

        // Redeeming the token proved the address, so there is no separate
        // verification step: signing in here is the assertion that the account is
        // usable the moment it is created.
        return signIn(email);
    }

    private SignedInSession signIn(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new SignedInSession(
                UUID.fromString(body.get("user").get("id").asText()),
                body.get("accessToken").asText(),
                result.getResponse().getCookie("refresh_token"));
    }

    /** The role the caller holds in one workspace, read the way the interface reads it. */
    private String roleIn(String bearer, UUID workspace) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode membership : json.readTree(result.getResponse().getContentAsString()).get("memberships")) {
            if (workspace.toString().equals(membership.get("workspaceId").asText())) {
                return membership.get("roleSlug").asText();
            }
        }
        throw new AssertionError("That account is not a member of " + workspace);
    }

    /** What one person has been told, read from their own feed rather than from the table. */
    private List<String> notificationTypesFor(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn();

        return values(result, "type");
    }

    private List<String> workspaceActivityActions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, adaToken)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        return values(result, "action");
    }

    // --- plumbing -------------------------------------------------------------

    private String body(Map<String, Object> content) {
        return json.writeValueAsString(content);
    }

    private String field(MvcResult result, String name) throws Exception {
        return json.readTree(result.getResponse().getContentAsString())
                .get(name)
                .asText();
    }

    private UUID uuid(MvcResult result, String name) throws Exception {
        return UUID.fromString(field(result, name));
    }

    /** One field from every item of a paged response. */
    private List<String> values(MvcResult result, String field) throws Exception {
        List<String> found = new ArrayList<>();
        for (JsonNode item : json.readTree(result.getResponse().getContentAsString()).get("content")) {
            found.add(item.get(field).asText());
        }
        return found;
    }

    /**
     * Retries an assertion until it holds.
     *
     * <p>Notifications and audit rows are written after the causing transaction commits, on their own
     * threads, which is what stops a writing request from holding two database connections at once.
     * Asserting immediately would be asserting against a race, and would fail on a loaded machine
     * rather than on a defect. Deliberately not a fixed sleep: this returns as soon as the row is
     * there, and only waits out the full period when something is genuinely wrong.
     */
    private static void eventually(ThrowingAssertion assertion) {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        Throwable lastFailure = null;

        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError | Exception e) {
                lastFailure = e;
            }

            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Still failing after " + PATIENCE.toSeconds() + "s of retries", lastFailure);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingAssertion {
        void run() throws Exception;
    }

    /** What signing in hands back: who you are, what to send, and what refreshes it. */
    private record SignedInSession(UUID userId, String accessToken, Cookie refreshCookie) {
        String header() {
            return "Bearer " + accessToken;
        }
    }
}
