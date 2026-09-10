package com.company.taskmanagementplatform.projects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * The rule that an employee views <em>assigned</em> projects, which comes straight from the
 * requirements.
 *
 * <p>This is the phase's most important test. Read scope is not a yes or no at the method boundary;
 * it decides which rows a listing returns, and a mistake in it leaks the shape of a workspace rather
 * than failing loudly. Three things put a project in reach of somebody without {@code
 * project:read_any}: owning it, being on it, and leading the team it belongs to. Each is covered
 * here on its own, and so is the case where none of them holds.
 */
class ProjectVisibilityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anEmployeeSeesOnlyTheProjectsTheyAreOn() throws Exception {
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");

        UUID assigned = project(fixture, null, null);
        project(fixture, null, null);
        project(fixture, null, null);
        addMember(fixture, assigned, employee.id());

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(assigned.toString()));
    }

    @Test
    void anEmployeeOnNoProjectSeesAnEmptyPageRatherThanAnError() throws Exception {
        // They belong to the workspace, so this is not a 404. There is simply
        // nothing assigned to them yet.
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");
        project(fixture, null, null);

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aProjectOutsideSomebodysReachAnswersAsMissing() throws Exception {
        // Not 403. A forbidden response would confirm the identifier names a real
        // project, which is the same disclosure the workspace guard exists to
        // prevent one level up.
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");
        UUID unreachable = project(fixture, null, null);

        mockMvc.perform(get(projectPath(fixture, unreachable))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));
    }

    @Test
    void owningAProjectIsEnoughToSeeIt() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID owned = project(fixture, lead.id(), null);

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(owned.toString()));
    }

    @Test
    void leadingTheTeamAProjectBelongsToIsEnoughToSeeIt() throws Exception {
        // Without being on the project at all, which is the case a membership-only
        // rule would miss.
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID teamId = teamLedBy(fixture, lead.id());
        UUID teamProject = project(fixture, null, teamId);
        project(fixture, null, null);

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(teamProject.toString()));

        mockMvc.perform(get(projectPath(fixture, teamProject))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(lead.id())))
                .andExpect(status().isOk());
    }

    @Test
    void anAdministratorSeesEveryProjectInTheWorkspace() throws Exception {
        Fixture fixture = workspace();
        project(fixture, null, null);
        project(fixture, null, null);
        project(fixture, null, null);

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void aFilterCannotWidenWhatSomebodyMaySee() throws Exception {
        // The visibility predicate is joined to the filters with AND, so asking for
        // somebody else's projects by owner returns nothing rather than theirs.
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");
        UserAccount lead = member(fixture, "TEAM_LEAD");
        project(fixture, lead.id(), null);

        mockMvc.perform(get(projectsPath(fixture))
                        .param("ownerUserId", lead.id().toString())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void losingProjectMembershipRemovesItFromTheirViewAtOnce() throws Exception {
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");
        UUID projectId = project(fixture, null, null);
        addMember(fixture, projectId, employee.id());
        String token = fixtures.bearer(employee.id());

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(projectPath(fixture, projectId) + "/members/" + employee.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        // Immediately, because nothing about the permission set or the visibility
        // is cached against the token.
        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(projectPath(fixture, projectId)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectsOfAnotherWorkspaceNeverAppear() throws Exception {
        Fixture mine = workspace();
        Fixture theirs = workspace();
        project(theirs, null, null);
        project(mine, null, null);

        mockMvc.perform(get(projectsPath(mine)).header(HttpHeaders.AUTHORIZATION, mine.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Visibility", uniqueSlug("visibility"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");
        return new Fixture(workspace.id(), fixtures.bearer(admin.id()));
    }

    private UserAccount member(Fixture fixture, String roleSlug) {
        UserAccount account = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(java.util.Locale.ROOT)));
        fixtures.addMember(fixture.workspaceId(), account.id(), roleSlug);
        return account;
    }

    private UUID project(Fixture fixture, UUID ownerUserId, UUID teamId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
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
                        .content(json.writeValueAsString(Map.of(
                                "name", "Team " + UUID.randomUUID().toString().substring(0, 8),
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

    private record Fixture(UUID workspaceId, String adminToken) {}
}
