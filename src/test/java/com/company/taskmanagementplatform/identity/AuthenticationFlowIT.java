package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.mail.MailMessage;
import com.company.taskmanagementplatform.common.mail.MailSender;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.RecordingMailSender;

import tools.jackson.databind.json.JsonMapper;

/** Registration, verification, sign-in, rotation and sign-out, driven through the API. */
class AuthenticationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private MailSender mailSender;

    private RecordingMailSender mail;

    @BeforeEach
    void setUp() {
        mail = (RecordingMailSender) mailSender;
        mail.clear();
    }

    @Test
    void registeringSendsAVerificationMessageAndIssuesNoTokens() throws Exception {
        String email = uniqueEmail("register");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(false))
                // No token of any kind: the address is not confirmed yet.
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("refresh_token"));

        assertThat(mail.lastToken(MailMessage.MailTemplate.EMAIL_VERIFICATION)).isPresent();
    }

    @Test
    void registeringNeverReturnsAPasswordHash() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        // The address is echoed back in the response, so it must not
                        // itself contain any of the words this test forbids. Naming it
                        // after what is being checked put "hash" in the body and failed
                        // the assertion on the fixture rather than on a leak.
                        .content(registration(uniqueEmail("signup"))))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("password", "hash", "$2");
    }

    @Test
    void anAccountWaitingForApprovalCanSignIn() throws Exception {
        // Onboarding by approval changed this. Somebody who has just registered is
        // told that a person has to let them in, rather than given an answer
        // indistinguishable from a wrong password. It grants them nothing: they
        // belong to no workspace, so there is no work to reach.
        String email = uniqueEmail("waiting");
        fixtures.pendingUser(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, IdentityFixtures.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void verifyingTheAddressAllowsSigningIn() throws Exception {
        String email = uniqueEmail("verify");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email)))
                .andExpect(status().isCreated());

        String token = mail.lastToken(MailMessage.MailTemplate.EMAIL_VERIFICATION).orElseThrow();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", token))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, IdentityFixtures.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aVerificationTokenWorksOnlyOnce() throws Exception {
        String email = uniqueEmail("single-use");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email)))
                .andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.EMAIL_VERIFICATION).orElseThrow();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", token))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.name()));
    }

    @Test
    void signingInSetsAProtectedRefreshCookie() throws Exception {
        String email = uniqueEmail("cookie");
        fixtures.verifiedUser(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, IdentityFixtures.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn();

        // The refresh token must never appear in the body; the cookie is the only
        // place it travels, and that is what makes it unreachable from a script.
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refresh");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");
    }

    @Test
    void aWrongPasswordIsRefusedGenerically() throws Exception {
        String email = uniqueEmail("wrong-password");
        fixtures.verifiedUser(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.name()));
    }

    @Test
    void anUnknownAddressIsRefusedIdentically() throws Exception {
        // Byte for byte the same answer as a wrong password, so the endpoint cannot
        // be used to find out who has an account.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(uniqueEmail("ghost"), "whatever")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.name()));
    }

    @Test
    void theAddressIsNotCaseSensitive() throws Exception {
        String email = uniqueEmail("mixed-case");
        fixtures.verifiedUser(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(email.toUpperCase(java.util.Locale.ROOT), IdentityFixtures.PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshingRotatesTheToken() throws Exception {
        Cookie first = signIn(uniqueEmail("rotate"));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie second = refreshed.getResponse().getCookie("refresh_token");
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void replayingAConsumedRefreshTokenEndsTheWholeSession() throws Exception {
        // The reuse rule. The value was in two places and only one holder can be
        // legitimate, so both are evicted.
        Cookie first = signIn(uniqueEmail("replay"));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized());

        // The successor is gone too, which is the part that matters.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshingWithNoCookieIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.name()));
    }

    @Test
    void signingOutRevokesTheTokenAndClearsTheCookie() throws Exception {
        Cookie cookie = signIn(uniqueEmail("logout"));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void describesTheCurrentSession() throws Exception {
        String email = uniqueEmail("me");
        var account = fixtures.verifiedUser(email);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(account.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.platformRole").doesNotExist())
                .andExpect(jsonPath("$.memberships").isArray());
    }

    @Test
    void everyResponseCarriesTheCorrelationHeader() throws Exception {
        // The foundation's contract, which the identity endpoints must not break.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(uniqueEmail("correlation"), "whatever")))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void rejectsAnInvalidRegistrationWithFieldLevelDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "email", "not-an-address",
                                "password", "short",
                                "firstName", "",
                                "lastName", "Person"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void resendingVerificationSaysNothingAboutWhetherTheAddressExists() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", uniqueEmail("ghost")))))
                .andExpect(status().isAccepted());

        assertThat(mail.countOf(MailMessage.MailTemplate.EMAIL_VERIFICATION)).isZero();
    }

    private Cookie signIn(String email) throws Exception {
        fixtures.verifiedUser(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, IdentityFixtures.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String registration(String email) throws Exception {
        return json.writeValueAsString(java.util.Map.of(
                "email", email,
                "password", IdentityFixtures.PASSWORD,
                "firstName", "Test",
                "lastName", "Person"));
    }

    private String login(String email, String password) throws Exception {
        return json.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }
}
