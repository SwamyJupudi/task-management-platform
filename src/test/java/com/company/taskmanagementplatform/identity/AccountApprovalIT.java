package com.company.taskmanagementplatform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * Onboarding, end to end: register, wait, be approved, work.
 *
 * <p>This is the flow that replaced onboarding by invitation, and the reason it replaced it is the
 * first test below. A registration is complete the moment it is made — nothing has to be delivered
 * to an address for somebody to reach the platform and be told where they stand. What they cannot do
 * is reach anybody's work, and that is not a rule stated anywhere new: they belong to no workspace,
 * and every workspace, project and task is scoped to a membership.
 */
class AccountApprovalIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void aNewRegistrationWaitsForApprovalAndCanStillSignIn() throws Exception {
        String email = uniqueEmail("waiting");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email", email,
                                "password", IdentityFixtures.PASSWORD,
                                "firstName", "Ada",
                                "lastName", "Lovelace"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aWaitingAccountReachesItsOwnProfileAndNoWorkspace() throws Exception {
        // The whole of what "cannot access workspace data" means here. There is no
        // new refusal: there is no membership, so there is nothing addressed to
        // them, and a workspace they have nothing to do with answers as missing
        // rather than as forbidden.
        UserAccount waiting = fixtures.pendingUser(uniqueEmail("no-workspace"));
        UserAccount owner = fixtures.verifiedUser(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Theirs", uniqueSlug("theirs"), owner.id());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(waiting.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.memberships").isEmpty());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(waiting.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorApprovesIntoAWorkspaceAndTheRoleTakesEffect() throws Exception {
        UserAccount admin = fixtures.superAdmin(uniqueEmail("platform"));
        UserAccount waiting = fixtures.pendingUser(uniqueEmail("approve-me"));
        WorkspaceResponse workspace = fixtures.workspace("Acme", uniqueSlug("acme"), admin.id());

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/pending-users/" + waiting.id() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleSlug", "EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Approved, placed, and holding the role the administrator named.
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(waiting.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.memberships[0].workspaceId").value(workspace.id().toString()))
                .andExpect(jsonPath("$.memberships[0].roleSlug").value("EMPLOYEE"));

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(waiting.id())))
                .andExpect(status().isOk());
    }

    @Test
    void aWaitingAccountAppearsInTheQueueTheAdminPanelReads() throws Exception {
        UserAccount admin = fixtures.superAdmin(uniqueEmail("platform-queue"));
        fixtures.pendingUser(uniqueEmail("in-the-queue"));

        // No second listing endpoint: a pending account is an account, and the
        // directory already filtered by status.
        mockMvc.perform(get("/api/v1/admin/accounts")
                        .param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void approvingAnAccountThatIsNotWaitingIsRefused() throws Exception {
        // Approving somebody already active would add a second workspace nobody
        // asked for, and approving a deactivated account would undo a decision.
        UserAccount admin = fixtures.superAdmin(uniqueEmail("platform-twice"));
        UserAccount already = fixtures.verifiedUser(uniqueEmail("already-active"));
        WorkspaceResponse workspace = fixtures.workspace("Twice", uniqueSlug("twice"), admin.id());

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/pending-users/" + already.id() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleSlug", "EMPLOYEE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWorkspaceAdministratorMayApproveIntoTheirOwnWorkspace() throws Exception {
        // The flow this exists for. Onboarding is not a platform-only act: the
        // person who runs a workspace decides who joins it.
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform-owner"));
        WorkspaceResponse workspace = fixtures.workspace("Theirs", uniqueSlug("theirs"), platform.id());
        UserAccount workspaceAdmin = fixtures.verifiedUser(uniqueEmail("ws-admin"));
        fixtures.addMember(workspace.id(), workspaceAdmin.id(), "ADMIN");
        UserAccount waiting = fixtures.pendingUser(uniqueEmail("admitted"));

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/pending-users/" + waiting.id() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(workspaceAdmin.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleSlug", "EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void aWorkspaceAdministratorMayNotApproveIntoAWorkspaceTheyDoNotAdminister() throws Exception {
        // Workspace isolation, unchanged. Administering one workspace grants
        // nothing in another, and the other answers as missing rather than as
        // forbidden so it cannot be discovered by guessing.
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform-two"));
        WorkspaceResponse theirs = fixtures.workspace("Theirs", uniqueSlug("mine"), platform.id());
        WorkspaceResponse other = fixtures.workspace("Other", uniqueSlug("other"), platform.id());
        UserAccount workspaceAdmin = fixtures.verifiedUser(uniqueEmail("ws-admin-two"));
        fixtures.addMember(theirs.id(), workspaceAdmin.id(), "ADMIN");
        UserAccount waiting = fixtures.pendingUser(uniqueEmail("not-theirs"));

        mockMvc.perform(post("/api/v1/workspaces/" + other.id() + "/pending-users/" + waiting.id() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(workspaceAdmin.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleSlug", "EMPLOYEE"))))
                .andExpect(status().isNotFound());

        // And the account is untouched by the attempt.
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(waiting.id())))
                .andExpect(jsonPath("$.user.status").value("PENDING_APPROVAL"));
    }

    @Test
    void aTeamLeadMayNotApproveAnybody() throws Exception {
        // TEAM_LEAD holds member:read and not member:invite. Seeing the roster is
        // not the same right as deciding who is on it.
        assertApprovalRefused("TEAM_LEAD", "lead");
    }

    @Test
    void anEmployeeMayNotApproveAnybody() throws Exception {
        assertApprovalRefused("EMPLOYEE", "employee");
    }

    @Test
    void aTeamLeadMayNotEvenSeeTheQueue() throws Exception {
        // Only somebody who could act on the queue is shown it.
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform-queue-guard"));
        WorkspaceResponse workspace = fixtures.workspace("Q", uniqueSlug("queue-guard"), platform.id());
        UserAccount lead = fixtures.verifiedUser(uniqueEmail("queue-lead"));
        fixtures.addMember(workspace.id(), lead.id(), "TEAM_LEAD");

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/pending-users")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWorkspaceAdministratorSeesTheQueue() throws Exception {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform-queue-ok"));
        WorkspaceResponse workspace = fixtures.workspace("Q", uniqueSlug("queue-ok"), platform.id());
        UserAccount workspaceAdmin = fixtures.verifiedUser(uniqueEmail("queue-admin"));
        fixtures.addMember(workspace.id(), workspaceAdmin.id(), "ADMIN");
        fixtures.pendingUser(uniqueEmail("queued"));

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/pending-users")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(workspaceAdmin.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"));
    }

    /** A member who holds no member:invite is refused, whatever else their role allows. */
    private void assertApprovalRefused(String roleSlug, String prefix) throws Exception {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform-" + prefix));
        WorkspaceResponse workspace = fixtures.workspace("W", uniqueSlug(prefix), platform.id());
        UserAccount member = fixtures.verifiedUser(uniqueEmail(prefix + "-member"));
        fixtures.addMember(workspace.id(), member.id(), roleSlug);
        UserAccount waiting = fixtures.pendingUser(uniqueEmail(prefix + "-waiting"));

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/pending-users/" + waiting.id() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(member.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleSlug", "EMPLOYEE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(waiting.id())))
                .andExpect(jsonPath("$.user.status").value("PENDING_APPROVAL"));
    }

    @Test
    void approvingWithARoleThatIsNotTheWorkspacesIsRefused() throws Exception {
        UserAccount admin = fixtures.superAdmin(uniqueEmail("platform-role"));
        UserAccount waiting = fixtures.pendingUser(uniqueEmail("bad-role"));
        WorkspaceResponse workspace = fixtures.workspace("Roles", uniqueSlug("roles"), admin.id());

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/pending-users/" + waiting.id() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleSlug", "SUPER_ADMIN"))))
                .andExpect(status().isBadRequest());

        // Still waiting: the whole approval rolled back with the membership.
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(waiting.id())))
                .andExpect(jsonPath("$.user.status").value("PENDING_APPROVAL"));
    }

    private String body(Map<String, ?> payload) {
        return json.writeValueAsString(payload);
    }
}
