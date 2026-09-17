package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
import com.company.taskmanagementplatform.users.UserAccount;

import tools.jackson.databind.json.JsonMapper;

/** Recovering a forgotten password, and changing a known one. */
class PasswordFlowIT extends AbstractIntegrationTest {

    private static final String NEW_PASSWORD = "a-different-password";

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
    void aForgottenPasswordRequestSaysNothingAboutWhetherTheAddressExists() throws Exception {
        // Reachable without signing in, so a distinguishable answer here would be a
        // way to enumerate the user base.
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", uniqueEmail("ghost")))))
                .andExpect(status().isAccepted());

        assertThat(mail.countOf(MailMessage.MailTemplate.PASSWORD_RESET)).isZero();
    }

    @Test
    void aKnownAddressReceivesAResetMessageAndTheSameAnswer() throws Exception {
        String email = uniqueEmail("reset");
        fixtures.verifiedUser(email);

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());

        assertThat(mail.lastToken(MailMessage.MailTemplate.PASSWORD_RESET)).isPresent();
    }

    @Test
    void resettingSetsTheNewPasswordAndRetiresTheOldOne() throws Exception {
        String email = uniqueEmail("reset-works");
        fixtures.verifiedUser(email);

        String token = requestReset(email);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", email, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aResetTokenWorksOnlyOnce() throws Exception {
        String email = uniqueEmail("reset-once");
        fixtures.verifiedUser(email);
        String token = requestReset(email);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", "another-one"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestingAnotherResetRetiresTheEarlierLink() throws Exception {
        // Three outstanding messages would be three ways in. Only the newest works.
        String email = uniqueEmail("reset-supersede");
        fixtures.verifiedUser(email);

        String first = requestReset(email);
        String second = requestReset(email);
        assertThat(first).isNotEqualTo(second);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", first, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aVerificationTokenCannotBeUsedToSetAPassword() throws Exception {
        // Different purposes, so a link for one is refused by the other.
        String email = uniqueEmail("wrong-kind");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "password", IdentityFixtures.PASSWORD,
                                "firstName", "Test",
                                "lastName", "Person"))))
                .andExpect(status().isCreated());

        // Asked for explicitly: registration no longer sends one.
        mockMvc.perform(post("/api/v1/auth/verify-email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());

        String verification = mail.lastToken(MailMessage.MailTemplate.EMAIL_VERIFICATION).orElseThrow();

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", verification, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.name()));
    }

    @Test
    void resettingEndsEverySessionWithoutException() throws Exception {
        // The person may be recovering an account somebody else took, so the session
        // to preserve might be the attacker's.
        String email = uniqueEmail("reset-sessions");
        fixtures.verifiedUser(email);
        Cookie refresh = signIn(email, IdentityFixtures.PASSWORD);

        String token = requestReset(email);
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingAPasswordRequiresTheCurrentOne() throws Exception {
        // An unattended browser is the case this defends against.
        UserAccount person = fixtures.verifiedUser(uniqueEmail("change-wrong"));

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", "not-it", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.name()));
    }

    @Test
    void changingAPasswordHandsBackAWorkingSession() throws Exception {
        String email = uniqueEmail("change-ok");
        UserAccount person = fixtures.verifiedUser(email);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", IdentityFixtures.PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", NEW_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void changingAPasswordEndsTheOtherSessions() throws Exception {
        String email = uniqueEmail("change-others");
        UserAccount person = fixtures.verifiedUser(email);
        Cookie elsewhere = signIn(email, IdentityFixtures.PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", IdentityFixtures.PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(elsewhere)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingAPasswordInvalidatesAccessTokensIssuedBeforeIt() throws Exception {
        // What the issue-time comparison in the authentication filter is for.
        String email = uniqueEmail("change-access");
        UserAccount person = fixtures.verifiedUser(email);
        String olderToken = fixtures.bearer(person.id());

        Thread.sleep(1100);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", IdentityFixtures.PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, olderToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED.name()));
    }

    @Test
    void aNewPasswordMustSatisfyThePolicy() throws Exception {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("weak"));

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", IdentityFixtures.PASSWORD, "newPassword", "short"))))
                .andExpect(status().isBadRequest());
    }

    private String requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());
        return mail.lastToken(MailMessage.MailTemplate.PASSWORD_RESET).orElseThrow();
    }

    private Cookie signIn(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
