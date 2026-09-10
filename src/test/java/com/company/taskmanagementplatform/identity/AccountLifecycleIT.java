package com.company.taskmanagementplatform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;

import tools.jackson.databind.json.JsonMapper;

/**
 * Activation and deactivation, and the thing that makes them mean anything: that they take effect at
 * once rather than whenever a token happens to expire.
 */
class AccountLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void deactivationTakesEffectOnTheVeryNextRequest() throws Exception {
        // The reason there is no cache in front of the account status read. With one,
        // a token issued a moment ago would keep working until the cache expired.
        UserAccount admin = fixtures.superAdmin(uniqueEmail("admin"));
        UserAccount victim = fixtures.verifiedUser(uniqueEmail("victim"));
        String victimToken = fixtures.bearer(victim.id());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, victimToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/" + victim.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, victimToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_INACTIVE.name()));
    }

    @Test
    void deactivationAlsoEndsEveryLiveSession() throws Exception {
        // A deactivated account with a working refresh token would be worse than one
        // that was never deactivated at all.
        UserAccount admin = fixtures.superAdmin(uniqueEmail("admin"));
        String email = uniqueEmail("sessions");
        UserAccount victim = fixtures.verifiedUser(email);

        MvcResult signIn = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                java.util.Map.of("email", email, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refresh = signIn.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/v1/users/" + victim.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void aDeactivatedAccountCannotSignInEvenWithTheRightPassword() throws Exception {
        UserAccount admin = fixtures.superAdmin(uniqueEmail("admin"));
        String email = uniqueEmail("blocked");
        UserAccount victim = fixtures.verifiedUser(email);

        mockMvc.perform(post("/api/v1/users/" + victim.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                java.util.Map.of("email", email, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_INACTIVE.name()));
    }

    @Test
    void reactivationRestoresAccess() throws Exception {
        UserAccount admin = fixtures.superAdmin(uniqueEmail("admin"));
        UserAccount member = fixtures.verifiedUser(uniqueEmail("restored"));
        String adminToken = fixtures.bearer(admin.id());

        mockMvc.perform(post("/api/v1/users/" + member.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/" + member.id() + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(member.id())))
                .andExpect(status().isOk());
    }

    @Test
    void removingAnAccountTakesItOffEveryRosterRatherThanLeavingAGhost() throws Exception {
        // A membership row outliving the person renders as a row with no address and
        // no name, because the account behind it is filtered out of every read.
        UserAccount admin = fixtures.superAdmin(uniqueEmail("admin"));
        var workspace = fixtures.workspace("Roster", uniqueSlug("roster"), admin.id());
        UserAccount leaving = fixtures.verifiedUser(uniqueEmail("leaving"));
        UserAccount staying = fixtures.verifiedUser(uniqueEmail("staying"));
        fixtures.addMember(workspace.id(), leaving.id(), "EMPLOYEE");
        fixtures.addMember(workspace.id(), staying.id(), "EMPLOYEE");

        String adminToken = fixtures.bearer(admin.id());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/users/" + leaving.id())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(staying.id().toString()))
                // Nothing null anywhere in the page.
                .andExpect(jsonPath("$.content[0].email").isNotEmpty());
    }

    @Test
    void deactivationKeepsTheMembershipBecauseThePersonIsExpectedBack() throws Exception {
        UserAccount admin = fixtures.superAdmin(uniqueEmail("admin"));
        var workspace = fixtures.workspace("Kept", uniqueSlug("kept"), admin.id());
        UserAccount paused = fixtures.verifiedUser(uniqueEmail("paused"));
        fixtures.addMember(workspace.id(), paused.id(), "EMPLOYEE");
        String adminToken = fixtures.bearer(admin.id());

        mockMvc.perform(post("/api/v1/users/" + paused.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userStatus").value("DEACTIVATED"));
    }

    @Test
    void anOrdinaryAccountCannotDeactivateAnybody() throws Exception {
        UserAccount ordinary = fixtures.verifiedUser(uniqueEmail("ordinary"));
        UserAccount other = fixtures.verifiedUser(uniqueEmail("other"));

        mockMvc.perform(post("/api/v1/users/" + other.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(ordinary.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    void aWorkspaceAdministratorHasNoPlatformReach() throws Exception {
        // Administering one workspace must never become a way to act across the
        // whole installation.
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        var workspace = fixtures.workspace("Reach", uniqueSlug("reach"), owner.id());
        UserAccount workspaceAdmin = fixtures.verifiedUser(uniqueEmail("ws-admin"));
        fixtures.addMember(workspace.id(), workspaceAdmin.id(), "ADMIN");

        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));

        mockMvc.perform(post("/api/v1/users/" + stranger.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(workspaceAdmin.id())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(workspaceAdmin.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPlatformAdministratorCanListAccounts() throws Exception {
        UserAccount admin = fixtures.superAdmin(uniqueEmail("lister"));

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void anybodyMayReadTheirOwnAccount() throws Exception {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("self"));

        mockMvc.perform(get("/api/v1/users/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id())))
                .andExpect(status().isOk());
    }

    @Test
    void nobodyMayReadSomebodyElsesAccountWithoutPermission() throws Exception {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("nosy"));
        UserAccount other = fixtures.verifiedUser(uniqueEmail("private"));

        mockMvc.perform(get("/api/v1/users/" + other.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id())))
                .andExpect(status().isForbidden());
    }
}
