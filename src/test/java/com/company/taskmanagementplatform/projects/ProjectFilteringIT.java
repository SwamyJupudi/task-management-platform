package com.company.taskmanagementplatform.projects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * Filtering, sorting and paging a project listing.
 *
 * <p>The sort allowlist is the part worth guarding. Passing a client's sort straight through lets a
 * query parameter probe the shape of the entity and order by columns with no index behind them, and
 * neither failure is visible from the response until somebody goes looking.
 */
class ProjectFilteringIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void filtersByStatus() throws Exception {
        Fixture fixture = workspace();
        UUID active = project(fixture, Map.of());
        project(fixture, Map.of());
        moveTo(fixture, active, "ACTIVE");

        mockMvc.perform(get(projectsPath(fixture))
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.toString()));
    }

    @Test
    void filtersByPriority() throws Exception {
        Fixture fixture = workspace();
        UUID critical = project(fixture, Map.of("priority", "CRITICAL"));
        project(fixture, Map.of("priority", "LOW"));

        mockMvc.perform(get(projectsPath(fixture))
                        .param("priority", "CRITICAL")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(critical.toString()));
    }

    @Test
    void filtersByOwnerAndByTeam() throws Exception {
        Fixture fixture = workspace();
        UserAccount owner = member(fixture, "TEAM_LEAD");
        UUID teamId = team(fixture);
        UUID owned = project(fixture, Map.of("ownerUserId", owner.id().toString()));
        UUID teamed = project(fixture, Map.of("teamId", teamId.toString()));
        project(fixture, Map.of());

        mockMvc.perform(get(projectsPath(fixture))
                        .param("ownerUserId", owner.id().toString())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(owned.toString()));

        mockMvc.perform(get(projectsPath(fixture))
                        .param("teamId", teamId.toString())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(teamed.toString()));
    }

    @Test
    void searchesNameAndKeyCaseInsensitively() throws Exception {
        Fixture fixture = workspace();
        project(fixture, Map.of("key", "ZEBRA1", "name", "Migration of the warehouse"));
        project(fixture, Map.of("key", "OTHER1", "name", "Something else entirely"));

        mockMvc.perform(get(projectsPath(fixture))
                        .param("q", "warehouse")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(projectsPath(fixture))
                        .param("q", "zebra")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtersByTag() throws Exception {
        Fixture fixture = workspace();
        UUID tagged = project(fixture, Map.of("labels", List.of("Backend", "Urgent")));
        project(fixture, Map.of("labels", List.of("Frontend")));

        mockMvc.perform(get(projectsPath(fixture))
                        .param("label", "backend")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(tagged.toString()));
    }

    @Test
    void filtersCombineWithEachOther() throws Exception {
        Fixture fixture = workspace();
        UUID wanted = project(fixture, Map.of("priority", "HIGH", "labels", List.of("Backend")));
        project(fixture, Map.of("priority", "HIGH", "labels", List.of("Frontend")));
        project(fixture, Map.of("priority", "LOW", "labels", List.of("Backend")));

        mockMvc.perform(get(projectsPath(fixture))
                        .param("priority", "HIGH")
                        .param("label", "Backend")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(wanted.toString()));
    }

    @Test
    void sortsByAnAllowedField() throws Exception {
        Fixture fixture = workspace();
        project(fixture, Map.of("name", "Bravo project"));
        project(fixture, Map.of("name", "Alpha project"));
        project(fixture, Map.of("name", "Charlie project"));

        mockMvc.perform(get(projectsPath(fixture))
                        .param("sort", "name,asc")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alpha project"))
                .andExpect(jsonPath("$.content[2].name").value("Charlie project"));
    }

    @Test
    void refusesASortOutsideTheAllowlist() throws Exception {
        // The whole point of the allowlist. Without it this would quietly succeed
        // and order by an internal column.
        Fixture fixture = workspace();
        project(fixture, Map.of());

        mockMvc.perform(get(projectsPath(fixture))
                        .param("sort", "createdByUserId,asc")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(projectsPath(fixture))
                        .param("sort", "deletedAt,desc")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAStatusOrPriorityThatIsNotOne() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(get(projectsPath(fixture))
                        .param("status", "CANCELLED")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(projectsPath(fixture))
                        .param("priority", "URGENT")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagesTheResults() throws Exception {
        Fixture fixture = workspace();
        for (int i = 0; i < 5; i++) {
            project(fixture, Map.of());
        }

        mockMvc.perform(get(projectsPath(fixture))
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void aDeletedProjectDropsOutOfEveryListing() throws Exception {
        Fixture fixture = workspace();
        UUID doomed = project(fixture, Map.of());
        project(fixture, Map.of());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(projectsPath(fixture) + "/" + doomed)
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(projectsPath(fixture)).header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Filtering", uniqueSlug("filtering"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");
        return new Fixture(workspace.id(), fixtures.bearer(admin.id()));
    }

    private UserAccount member(Fixture fixture, String roleSlug) {
        UserAccount account = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(java.util.Locale.ROOT)));
        fixtures.addMember(fixture.workspaceId(), account.id(), roleSlug);
        return account;
    }

    private UUID project(Fixture fixture, Map<String, Object> overrides) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("key", uniqueKey());
        body.put("name", uniqueName());
        body.putAll(overrides);

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

    private void moveTo(Fixture fixture, UUID projectId, String status) throws Exception {
        mockMvc.perform(post(projectsPath(fixture) + "/" + projectId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private static String projectsPath(Fixture fixture) {
        return "/api/v1/workspaces/" + fixture.workspaceId() + "/projects";
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(java.util.Locale.ROOT);
    }

    private static String uniqueName() {
        return "Project " + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(UUID workspaceId, String adminToken) {}
}
