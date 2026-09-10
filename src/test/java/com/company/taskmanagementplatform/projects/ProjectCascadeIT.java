package com.company.taskmanagementplatform.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * What happens to projects when something underneath them goes away.
 *
 * <p>Project membership and project ownership are foreign keys into {@code workspace_members}, so
 * this is not cosmetic tidying: without the cleanup, removing somebody from a workspace is a write
 * the database refuses outright. {@code ProjectSchemaIT} proves the refusal; this proves the cleanup
 * that avoids it, through the API and the events rather than by calling a listener directly.
 *
 * <p>A deleted team is the other case. Nothing refuses that one, because a soft delete leaves the
 * row in place, which is exactly why the project has to be detached deliberately.
 */
class ProjectCascadeIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAccountService users;

    @Test
    void removingAWorkspaceMemberTakesThemOffItsProjects() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID projectId = project(fixture, null, null);
        addMember(fixture, projectId, person.id());

        assertThat(projectMemberships(person.id())).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(projectMemberships(person.id())).isZero();
    }

    @Test
    void removingAnOwnerFromTheWorkspaceLeavesTheProjectWithoutOne() throws Exception {
        // The project survives. Choosing a replacement is not a decision the
        // cleanup is in a position to make, so it clears the column and stops.
        Fixture fixture = workspace();
        UserAccount owner = member(fixture, "TEAM_LEAD");
        UUID projectId = project(fixture, owner.id(), null);

        assertThat(ownerOf(projectId)).isEqualTo(owner.id());

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(ownerOf(projectId)).isNull();
        assertThat(projectStillExists(projectId)).isTrue();
        assertThat(projectMemberships(owner.id())).isZero();
    }

    @Test
    void deletingAnAccountClearsItsProjectStateEverywhere() throws Exception {
        Fixture first = workspace();
        Fixture second = workspace();
        UserAccount person = fixtures.verifiedUser(uniqueEmail("everywhere"));
        fixtures.addMember(first.workspaceId(), person.id(), "TEAM_LEAD");
        fixtures.addMember(second.workspaceId(), person.id(), "TEAM_LEAD");

        UUID firstProject = project(first, person.id(), null);
        UUID secondProject = project(second, person.id(), null);

        users.softDelete(person.id());

        assertThat(ownerOf(firstProject)).isNull();
        assertThat(ownerOf(secondProject)).isNull();
        assertThat(projectMemberships(person.id())).isZero();
    }

    @Test
    void deletingATeamDetachesItsProjectsAndLeavesThemIntact() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = team(fixture);
        UUID projectId = project(fixture, null, teamId);

        assertThat(teamOf(projectId)).isEqualTo(teamId);

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/teams/" + teamId)
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(teamOf(projectId)).isNull();
        assertThat(projectStillExists(projectId)).isTrue();
    }

    @Test
    void deletingATeamLeavesOtherTeamsProjectsAlone() throws Exception {
        Fixture fixture = workspace();
        UUID doomed = team(fixture);
        UUID surviving = team(fixture);
        UUID doomedProject = project(fixture, null, doomed);
        UUID survivingProject = project(fixture, null, surviving);

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/teams/" + doomed)
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(teamOf(doomedProject)).isNull();
        assertThat(teamOf(survivingProject)).isEqualTo(surviving);
    }

    @Test
    void theProjectsOfOtherPeopleAreUntouched() throws Exception {
        Fixture fixture = workspace();
        UserAccount leaving = member(fixture, "EMPLOYEE");
        UserAccount staying = member(fixture, "EMPLOYEE");
        UUID projectId = project(fixture, null, null);
        addMember(fixture, projectId, leaving.id());
        addMember(fixture, projectId, staying.id());

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + leaving.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(projectMemberships(staying.id())).isEqualTo(1);
        assertThat(projectMemberships(leaving.id())).isZero();
    }

    @Test
    void aMembershipInAnotherWorkspaceSurvivesRemovalFromThisOne() throws Exception {
        Fixture first = workspace();
        Fixture second = workspace();
        UserAccount person = fixtures.verifiedUser(uniqueEmail("dual"));
        fixtures.addMember(first.workspaceId(), person.id(), "EMPLOYEE");
        fixtures.addMember(second.workspaceId(), person.id(), "EMPLOYEE");

        addMember(first, project(first, null, null), person.id());
        addMember(second, project(second, null, null), person.id());

        mockMvc.perform(delete("/api/v1/workspaces/" + first.workspaceId() + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, first.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(projectMemberships(person.id())).isEqualTo(1);
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Cascade", uniqueSlug("cascade"), owner.id());
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

        String response = mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/projects")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private UUID team(Fixture fixture) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/teams")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Team " + UUID.randomUUID().toString().substring(0, 8)))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private void addMember(Fixture fixture, UUID projectId, UUID userId) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/projects/" + projectId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", userId.toString()))))
                .andExpect(status().isCreated());
    }

    private Integer projectMemberships(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM project_members WHERE user_id = ?", Integer.class, userId);
    }

    private UUID ownerOf(UUID projectId) {
        return jdbc.queryForObject("SELECT owner_user_id FROM projects WHERE id = ?", UUID.class, projectId);
    }

    private UUID teamOf(UUID projectId) {
        return jdbc.queryForObject("SELECT team_id FROM projects WHERE id = ?", UUID.class, projectId);
    }

    private boolean projectStillExists(UUID projectId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM projects WHERE id = ? AND deleted_at IS NULL", Integer.class, projectId);
        return count != null && count == 1;
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(java.util.Locale.ROOT);
    }

    private static String uniqueName() {
        return "Project " + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(UUID workspaceId, String adminToken) {}
}
