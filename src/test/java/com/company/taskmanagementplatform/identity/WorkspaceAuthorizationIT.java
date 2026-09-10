package com.company.taskmanagementplatform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * Role-based access inside a workspace, and the boundary around it.
 *
 * <p>Two rules are worth watching in particular: that a workspace the caller has nothing to do with
 * answers as missing rather than as forbidden, and that the platform administrator reaches every
 * workspace through ordinary permission rows rather than a special case.
 */
class WorkspaceAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anAdministratorMayInviteAndReassign() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Team", uniqueSlug("team"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");

        UserAccount employee = fixtures.verifiedUser(uniqueEmail("employee"));
        fixtures.addMember(workspace.id(), employee.id(), "EMPLOYEE");

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", uniqueEmail("invitee"), "roleSlug", "EMPLOYEE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/workspaces/" + workspace.id() + "/members/" + employee.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roleSlug", "TEAM_LEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleSlug").value("TEAM_LEAD"));
    }

    @Test
    void anEmployeeMaySeeTheRosterButNotChangeIt() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Team", uniqueSlug("team"), owner.id());
        UserAccount employee = fixtures.verifiedUser(uniqueEmail("employee"));
        fixtures.addMember(workspace.id(), employee.id(), "EMPLOYEE");
        String token = fixtures.bearer(employee.id());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", uniqueEmail("nope"), "roleSlug", "EMPLOYEE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    void aTeamLeadMayNotReassignRoles() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Team", uniqueSlug("team"), owner.id());
        UserAccount lead = fixtures.verifiedUser(uniqueEmail("lead"));
        UserAccount employee = fixtures.verifiedUser(uniqueEmail("employee"));
        fixtures.addMember(workspace.id(), lead.id(), "TEAM_LEAD");
        fixtures.addMember(workspace.id(), employee.id(), "EMPLOYEE");

        mockMvc.perform(patch("/api/v1/workspaces/" + workspace.id() + "/members/" + employee.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roleSlug", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStrangerSeesAWorkspaceAsMissingRatherThanForbidden() throws Exception {
        // A 403 would confirm the identifier names something real, which is enough
        // to map the installation one guess at a time.
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Private", uniqueSlug("private"), owner.id());
        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(stranger.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));
    }

    @Test
    void aWorkspaceThatDoesNotExistAnswersTheSameWay() throws Exception {
        // Indistinguishable from the case above, which is the point.
        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));

        mockMvc.perform(get("/api/v1/workspaces/" + UUID.randomUUID() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(stranger.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));
    }

    @Test
    void membershipOfOneWorkspaceGrantsNothingInAnother() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse mine = fixtures.workspace("Mine", uniqueSlug("mine"), owner.id());
        WorkspaceResponse theirs = fixtures.workspace("Theirs", uniqueSlug("theirs"), owner.id());

        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(mine.id(), admin.id(), "ADMIN");

        mockMvc.perform(get("/api/v1/workspaces/" + theirs.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(admin.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePlatformAdministratorReachesAWorkspaceWithoutBelongingToIt() throws Exception {
        // Through ordinary role_permissions rows, not through a bypass in the
        // authorization path. There is deliberately no such bypass to test.
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Any", uniqueSlug("any"), owner.id());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformRole").value("SUPER_ADMIN"))
                // No membership row anywhere, and none needed.
                .andExpect(jsonPath("$.memberships").isEmpty());
    }

    @Test
    void onlyAPlatformAdministratorMayCreateAWorkspace() throws Exception {
        UserAccount ordinary = fixtures.verifiedUser(uniqueEmail("ordinary"));

        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(ordinary.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Sneaky", "slug", uniqueSlug("sneaky")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingAWorkspaceSeedsItsRoles() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));

        String body = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Fresh", "slug", uniqueSlug("fresh")))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID workspaceId = UUID.fromString(json.readTree(body).get("id").asText());

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void refusesARoleSlugThatDoesNotExistInThisWorkspace() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse first = fixtures.workspace("First", uniqueSlug("first"), owner.id());
        UserAccount member = fixtures.verifiedUser(uniqueEmail("member"));
        fixtures.addMember(first.id(), member.id(), "EMPLOYEE");

        // A slug that exists in every workspace, but resolved within this one, so
        // the request cannot reach across even by naming a real role.
        mockMvc.perform(patch("/api/v1/workspaces/" + first.id() + "/members/" + member.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roleSlug", "NOT_A_ROLE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removingAMemberEndsTheirAccessToThatWorkspace() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Team", uniqueSlug("team"), owner.id());
        UserAccount employee = fixtures.verifiedUser(uniqueEmail("employee"));
        fixtures.addMember(workspace.id(), employee.id(), "EMPLOYEE");
        String token = fixtures.bearer(employee.id());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/workspaces/" + workspace.id() + "/members/" + employee.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id())))
                .andExpect(status().isNoContent());

        // Immediately, because the permission set is read rather than cached.
        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRoleChangeTakesEffectAtOnce() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Team", uniqueSlug("team"), owner.id());
        UserAccount person = fixtures.verifiedUser(uniqueEmail("promoted"));
        fixtures.addMember(workspace.id(), person.id(), "EMPLOYEE");
        String token = fixtures.bearer(person.id());

        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", uniqueEmail("x"), "roleSlug", "EMPLOYEE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/workspaces/" + workspace.id() + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roleSlug", "ADMIN"))))
                .andExpect(status().isOk());

        // The same token, now carrying more authority, because nothing about the
        // permission set was cached against it.
        mockMvc.perform(post("/api/v1/workspaces/" + workspace.id() + "/invitations")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", uniqueEmail("y"), "roleSlug", "EMPLOYEE"))))
                .andExpect(status().isCreated());
    }
}
