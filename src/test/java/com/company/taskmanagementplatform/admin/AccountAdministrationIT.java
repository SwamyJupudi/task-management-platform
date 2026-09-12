package com.company.taskmanagementplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.RecordingMailSender;
import com.company.taskmanagementplatform.users.UserAccount;

/**
 * The four account verbs the admin panel adds, and the refusals that come with them.
 *
 * <p>{@code user:update} has been in the permission catalog since phase two granted to nobody, so
 * these endpoints are reachable through a platform role alone. That is the decision rather than an
 * omission: renaming somebody who may also work in three other workspaces is platform
 * administration, and {@code @perm.onPlatform} never consults workspace membership.
 *
 * <p><strong>There is deliberately no endpoint that sets a password.</strong> An administrator
 * starts a recovery and the token goes to the address that owns the account, which is what the
 * recovery test below actually proves by completing the reset with it.
 */
class AccountAdministrationIT extends AdminApiTestBase {

    /**
     * Injected by its interface and cast, as every other test that reads a token does. The recording
     * transport is registered as the primary {@code MailSender} for the whole suite.
     */
    @Autowired
    private com.company.taskmanagementplatform.common.mail.MailSender mailSender;

    private RecordingMailSender mail;

    @org.junit.jupiter.api.BeforeEach
    void captureMail() {
        mail = (RecordingMailSender) mailSender;
        mail.clear();
    }

    @Test
    void editsSomebodyElsesName() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("subject"));

        mockMvc.perform(patch("/api/v1/users/" + subject.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.lastName").value("Hopper"))
                // The address is the account's identity and is not editable here.
                .andExpect(jsonPath("$.email").value(subject.email()));
    }

    @Test
    void refusesABlankName() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("subject"));

        mockMvc.perform(patch("/api/v1/users/" + subject.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"   \",\"lastName\":\"Hopper\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unlockingIsIdempotent() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("subject"));
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
    }

    @Test
    void startsARecoveryThatTheAccountItselfCanComplete() throws Exception {
        Estate estate = estate();
        String email = uniqueEmail("forgetful");
        UserAccount subject = fixtures.verifiedUser(email);

        mail.clear();
        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isNoContent());

        // The token is mailed to the account's own address. The administrator never
        // sees it, which is the whole reason there is no set-password endpoint.
        String token = mail.lastMessageTo(email)
                .map(message -> message.variables().get("token"))
                .orElseThrow();

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                java.util.Map.of("token", token, "newPassword", "a-brand-new-password"))))
                .andExpect(status().isOk());

        // And the reset reaches the same flow the public one does, so the new
        // password works and the old one no longer does.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                java.util.Map.of("email", email, "password", "a-brand-new-password"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                java.util.Map.of("email", email, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRecoveryForAnAccountThatDoesNotExistIs404() throws Exception {
        Estate estate = estate();

        // Unlike the public forgotten-password endpoint, which answers identically
        // either way so it cannot be used to test addresses. This caller already
        // holds user:update on a platform role and can list every account, so an
        // honest answer discloses nothing new.
        mockMvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void resendsVerificationAndRefusesForAnAddressAlreadyConfirmed() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        UserAccount pending = fixtures.pendingUser(uniqueEmail("pending"));
        mockMvc.perform(post("/api/v1/users/" + pending.id() + "/resend-verification")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isNoContent());

        UserAccount verified = fixtures.verifiedUser(uniqueEmail("verified"));
        mockMvc.perform(post("/api/v1/users/" + verified.id() + "/resend-verification")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isConflict());
    }

    // --- the self-harm guards ------------------------------------------------

    @Test
    void youCannotSwitchOffYourOwnAccount() throws Exception {
        Estate estate = estate();

        mockMvc.perform(post("/api/v1/users/" + estate.platformAdminId() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isConflict());
    }

    @Test
    void youCannotRemoveYourOwnAccount() throws Exception {
        Estate estate = estate();

        mockMvc.perform(delete("/api/v1/users/" + estate.platformAdminId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isConflict());
    }

    @Test
    void youCannotRevokeYourOwnPlatformRole() throws Exception {
        Estate estate = estate();

        mockMvc.perform(delete("/api/v1/users/" + estate.platformAdminId() + "/platform-role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isConflict());
    }

    @Test
    void aWorkspaceAdministratorIsAnOrdinaryAccountAndMayBeSwitchedOff() throws Exception {
        Estate estate = estate();

        // The guards are about the platform role and about yourself, not about
        // administrators in general.
        mockMvc.perform(post("/api/v1/users/" + estate.firstAdminId() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }

    @Test
    void theAccountDirectoryReportsLockStateAndWorkspaceReach() throws Exception {
        Estate estate = estate();

        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(PLATFORM_ACCOUNTS)
                        .param("search", "first-admin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The two facts this listing adds over the ordinary directory.
        assertThat(body).contains("\"workspaceCount\":1");
        assertThat(body).contains("\"platformAdministrator\":false");
    }
}
