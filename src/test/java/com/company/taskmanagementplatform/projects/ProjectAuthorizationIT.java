package com.company.taskmanagementplatform.projects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Who may do what to a project, and to which projects.
 *
 * <p>Projects carry both halves of the scope layer, which teams do not, so this covers more than its
 * team counterpart. Write scope narrows a team lead to the projects they own or whose team they
 * lead. Read scope narrows anybody without {@code project:read_any} to the projects assigned to
 * them, and a project outside that reach answers as missing rather than as forbidden, exactly as one
 * from another workspace does.
 */
class ProjectAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anEmployeeMayNotCreateAProject() throws Exception {
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");

        mockMvc.perform(post(projectsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("key", uniqueKey(), "name", uniqueName()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    void anEmployeeOnAProjectMaySeeItButNotChangeIt() throws Exception {
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");
        UUID projectId = projectId(fixture, null, null);
        addMember(fixture, projectId, employee.id());
        String token = fixtures.bearer(employee.id());

        mockMvc.perform(get(projectPath(fixture, projectId)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", uniqueName()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(projectPath(fixture, projectId)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeamLeadMayChangeAProjectTheyOwn() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID projectId = projectId(fixture, lead.id(), null);

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Mine to run"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Mine to run"));
    }

    @Test
    void aTeamLeadMayChangeAProjectBelongingToATeamTheyLead() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID teamId = teamLedBy(fixture, lead.id());
        UUID projectId = projectId(fixture, null, teamId);

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "My team runs this"))))
                .andExpect(status().isOk());
    }

    @Test
    void aTeamLeadMayNotChangeAProjectThatIsNeitherTheirsNorTheirTeams() throws Exception {
        // Same permission, different row. This is the write scope doing its job.
        // They are made a plain member so the project is visible to them, which is
        // what makes 403 the right answer rather than 404.
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID somebodyElses = projectId(fixture, null, null);
        addMember(fixture, somebodyElses, lead.id());

        mockMvc.perform(patch(projectPath(fixture, somebodyElses))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Not mine"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    void aTeamLeadMayNotCreateOrDeleteAProjectEvenTheirOwn() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID theirs = projectId(fixture, lead.id(), null);
        String token = fixtures.bearer(lead.id());

        mockMvc.perform(post(projectsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("key", uniqueKey(), "name", uniqueName()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(projectPath(fixture, theirs)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeamLeadManagesTheRosterOfTheirOwnProjectOnly() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UserAccount employee = member(fixture, "EMPLOYEE");
        UUID theirs = projectId(fixture, lead.id(), null);
        UUID somebodyElses = projectId(fixture, null, null);
        addMember(fixture, somebodyElses, lead.id());
        String token = fixtures.bearer(lead.id());

        mockMvc.perform(post(projectPath(fixture, theirs) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", employee.id().toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(projectPath(fixture, somebodyElses) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", employee.id().toString()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(projectPath(fixture, somebodyElses) + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", employee.id().toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorReachesEveryProjectInTheWorkspace() throws Exception {
        // Because they hold project:manage_any, which widens the same code the lead
        // holds from "mine" to "all of them".
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID somebodyElses = projectId(fixture, lead.id(), null);

        mockMvc.perform(patch(projectPath(fixture, somebodyElses))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Administered"))))
                .andExpect(status().isOk());
    }

    @Test
    void aStrangerSeesTheProjectsOfAWorkspaceAsMissing() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, null, null);
        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));
        String token = fixtures.bearer(stranger.id());

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));

        mockMvc.perform(get(projectPath(fixture, projectId)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProjectFromAnotherWorkspaceIsMissingRatherThanForbidden() throws Exception {
        Fixture mine = workspace();
        Fixture theirs = workspace();
        UUID foreign = projectId(theirs, null, null);

        mockMvc.perform(get(projectPath(mine, foreign)).header(HttpHeaders.AUTHORIZATION, mine.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));

        mockMvc.perform(patch(projectPath(mine, foreign))
                        .header(HttpHeaders.AUTHORIZATION, mine.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", uniqueName()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProjectThatDoesNotExistAnswersTheSameWay() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(get(projectPath(fixture, UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePlatformAdministratorReachesProjectsWithoutBelongingToTheWorkspace() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, null, null);

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(fixture.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "By the platform"))))
                .andExpect(status().isOk());
    }

    @Test
    void aRoleChangeChangesWhatSomebodyMayDoAtOnce() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID projectId = projectId(fixture, null, null);
        addMember(fixture, projectId, person.id());
        String token = fixtures.bearer(person.id());

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Too soon"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roleSlug", "ADMIN"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Now allowed"))))
                .andExpect(status().isOk());
    }

    @Test
    void anArchivedWorkspaceFreezesEveryProjectChange() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, null, null);

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post(projectsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("key", uniqueKey(), "name", uniqueName()))))
                .andExpect(status().isConflict());

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Frozen"))))
                .andExpect(status().isConflict());

        // Still readable while frozen.
        mockMvc.perform(get(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(get(projectsPath(fixture))).andExpect(status().isUnauthorized());
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Projects", uniqueSlug("projects"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");
        return new Fixture(workspace.id(), owner.id(), fixtures.bearer(admin.id()));
    }

    private UserAccount member(Fixture fixture, String roleSlug) {
        UserAccount account = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(java.util.Locale.ROOT)));
        fixtures.addMember(fixture.workspaceId(), account.id(), roleSlug);
        return account;
    }

    private UUID projectId(Fixture fixture, UUID ownerUserId, UUID teamId) throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("key", uniqueKey());
        body.put("name", uniqueName());
        if (ownerUserId != null) {
            body.put("ownerUserId", ownerUserId.toString());
        }
        if (teamId != null) {
            body.put("teamId", teamId.toString());
        }

        String response = mockMvc.perform(post(projectsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private UUID teamLedBy(Fixture fixture, UUID leadUserId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/teams")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Team " + UUID.randomUUID().toString().substring(0, 8),
                                        "leadUserId", leadUserId.toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private void addMember(Fixture fixture, UUID projectId, UUID userId) throws Exception {
        mockMvc.perform(post(projectPath(fixture, projectId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", userId.toString()))))
                .andExpect(status().isCreated());
    }

    private static String projectsPath(Fixture fixture) {
        return "/api/v1/workspaces/" + fixture.workspaceId() + "/projects";
    }

    private static String projectPath(Fixture fixture, UUID projectId) {
        return projectsPath(fixture) + "/" + projectId;
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(java.util.Locale.ROOT);
    }

    private static String uniqueName() {
        return "Project " + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(UUID workspaceId, UUID ownerId, String adminToken) {}
}
