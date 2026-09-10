package com.company.taskmanagementplatform.projects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
 * Creating projects, editing them, moving them through the lifecycle, and managing their rosters.
 *
 * <p>Driven through the API, because these are the paths a client actually takes.
 */
class ProjectManagementIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anAdministratorCreatesAProject() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(createProject(fixture, body(Map.of(
                        "key", "PLAT",
                        "name", "Platform Rebuild",
                        "description", "Long overdue",
                        "priority", "HIGH"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("PLAT"))
                .andExpect(jsonPath("$.name").value("Platform Rebuild"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                // Always PLANNING, whatever the caller might have wanted.
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.memberCount").value(0))
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());
    }

    @Test
    void aLowercaseKeyIsStoredUppercase() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(createProject(fixture, body(Map.of("key", "plat", "name", uniqueName()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("PLAT"));
    }

    @Test
    void namingAnOwnerAtCreationAlsoPutsThemOnTheProject() throws Exception {
        Fixture fixture = workspace();
        UserAccount owner = member(fixture, "TEAM_LEAD");

        mockMvc.perform(createProject(
                        fixture,
                        body(Map.of("key", uniqueKey(), "name", uniqueName(), "ownerUserId", owner.id().toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(owner.id().toString()))
                .andExpect(jsonPath("$.ownerEmail").value(owner.email()))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void refusesAnOwnerWhoIsNotInTheWorkspace() throws Exception {
        Fixture fixture = workspace();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));

        mockMvc.perform(createProject(
                        fixture,
                        body(Map.of(
                                "key", uniqueKey(), "name", uniqueName(), "ownerUserId", outsider.id().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesADuplicateKeyOrName() throws Exception {
        Fixture fixture = workspace();
        String key = uniqueKey();
        String name = uniqueName();
        mockMvc.perform(createProject(fixture, body(Map.of("key", key, "name", name))))
                .andExpect(status().isCreated());

        mockMvc.perform(createProject(fixture, body(Map.of("key", key, "name", uniqueName()))))
                .andExpect(status().isConflict());
        mockMvc.perform(createProject(fixture, body(Map.of("key", uniqueKey(), "name", name))))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesAProjectThatEndsBeforeItStarts() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(createProject(fixture, body(Map.of(
                        "key", uniqueKey(),
                        "name", uniqueName(),
                        "startDate", "2026-06-01",
                        "endDate", "2026-05-01"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAKeyThatIsNotTheAgreedShape() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(createProject(fixture, body(Map.of("key", "1BAD", "name", uniqueName()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingLeavesOmittedFieldsAlone() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", "Before", "description", "Kept"));

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "After"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.description").value("Kept"));
    }

    @Test
    void tagsAreReplacedAsASetAndFoldedOntoOneLabel() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(
                fixture,
                Map.of("key", uniqueKey(), "name", uniqueName(), "labels", List.of("Backend", "backend", "Urgent")));

        mockMvc.perform(get(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(2));

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("labels", List.of("Frontend")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(1))
                .andExpect(jsonPath("$.labels[0]").value("Frontend"));

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("labels", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(0));
    }

    @Test
    void aProjectMovesThroughItsLifecycle() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        moveTo(fixture, projectId, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        moveTo(fixture, projectId, "ON_HOLD").andExpect(status().isOk());
        moveTo(fixture, projectId, "ACTIVE").andExpect(status().isOk());
        moveTo(fixture, projectId, "COMPLETED").andExpect(status().isOk());
        moveTo(fixture, projectId, "ARCHIVED").andExpect(status().isOk());
        moveTo(fixture, projectId, "ACTIVE").andExpect(status().isOk());
    }

    @Test
    void refusesAMoveTheLifecycleDoesNotAllow() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        // PLANNING cannot skip to COMPLETED or pause before it has started.
        moveTo(fixture, projectId, "COMPLETED").andExpect(status().isConflict());
        moveTo(fixture, projectId, "ON_HOLD").andExpect(status().isConflict());
    }

    @Test
    void refusesAMoveToTheStatusItAlreadyHolds() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        moveTo(fixture, projectId, "PLANNING").andExpect(status().isConflict());
    }

    @Test
    void refusesAStatusThatIsNotOne() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        moveTo(fixture, projectId, "CANCELLED").andExpect(status().isBadRequest());
    }

    @Test
    void anArchivedProjectRefusesEditsAndStaysReadable() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        moveTo(fixture, projectId, "ARCHIVED").andExpect(status().isOk());

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", uniqueName()))))
                .andExpect(status().isConflict());

        mockMvc.perform(post(projectPath(fixture, projectId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", person.id().toString()))))
                .andExpect(status().isConflict());

        mockMvc.perform(get(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        // And bringing it back makes it editable again.
        moveTo(fixture, projectId, "ACTIVE").andExpect(status().isOk());
        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Back"))))
                .andExpect(status().isOk());
    }

    @Test
    void deletingHidesTheProjectAndFreesItsKeyAndName() throws Exception {
        Fixture fixture = workspace();
        String key = uniqueKey();
        String name = uniqueName();
        UUID projectId = projectId(fixture, Map.of("key", key, "name", name));

        mockMvc.perform(delete(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(createProject(fixture, body(Map.of("key", key, "name", name))))
                .andExpect(status().isCreated());
    }

    @Test
    void membersAreAddedListedAndRemoved() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        mockMvc.perform(post(projectPath(fixture, projectId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", person.id().toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(person.email()))
                .andExpect(jsonPath("$.owner").value(false));

        mockMvc.perform(get(projectPath(fixture, projectId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete(projectPath(fixture, projectId) + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void refusesAMemberWhoDoesNotBelongToTheWorkspace() throws Exception {
        Fixture fixture = workspace();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        mockMvc.perform(post(projectPath(fixture, projectId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", outsider.id().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assigningAnOwnerAddsThemToTheProjectIfTheyAreNotOnIt() throws Exception {
        Fixture fixture = workspace();
        UserAccount owner = member(fixture, "TEAM_LEAD");
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        mockMvc.perform(put(projectPath(fixture, projectId) + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", owner.id().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(owner.id().toString()))
                .andExpect(jsonPath("$.memberCount").value(1));

        mockMvc.perform(get(projectPath(fixture, projectId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].owner").value(true));
    }

    @Test
    void removingTheOwnerFromTheirOwnProjectIsRefused() throws Exception {
        Fixture fixture = workspace();
        UserAccount owner = member(fixture, "TEAM_LEAD");
        UUID projectId = projectId(
                fixture, Map.of("key", uniqueKey(), "name", uniqueName(), "ownerUserId", owner.id().toString()));

        mockMvc.perform(delete(projectPath(fixture, projectId) + "/members/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void clearingTheOwnerLeavesThemOnTheProject() throws Exception {
        Fixture fixture = workspace();
        UserAccount owner = member(fixture, "TEAM_LEAD");
        UUID projectId = projectId(
                fixture, Map.of("key", uniqueKey(), "name", uniqueName(), "ownerUserId", owner.id().toString()));

        mockMvc.perform(delete(projectPath(fixture, projectId) + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.memberCount").value(1));

        mockMvc.perform(delete(projectPath(fixture, projectId) + "/members/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void clearingAnOwnerThatIsNotThereIsAConflict() throws Exception {
        Fixture fixture = workspace();
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        mockMvc.perform(delete(projectPath(fixture, projectId) + "/owner")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void aProjectCanBeAttachedToATeamOfItsWorkspaceAndDetached() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, "Platform");
        UUID projectId = projectId(fixture, Map.of("key", uniqueKey(), "name", uniqueName()));

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("teamId", teamId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(teamId.toString()))
                .andExpect(jsonPath("$.teamName").value("Platform"));

        mockMvc.perform(patch(projectPath(fixture, projectId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("clearTeam", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").doesNotExist());
    }

    @Test
    void refusesATeamFromAnotherWorkspace() throws Exception {
        Fixture mine = workspace();
        Fixture theirs = workspace();
        UUID foreignTeam = teamId(theirs, "Elsewhere");
        UUID projectId = projectId(mine, Map.of("key", uniqueKey(), "name", uniqueName()));

        mockMvc.perform(patch(projectPath(mine, projectId))
                        .header(HttpHeaders.AUTHORIZATION, mine.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("teamId", foreignTeam.toString()))))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Projects", uniqueSlug("projects"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");
        return new Fixture(workspace.id(), fixtures.bearer(admin.id()));
    }

    private UserAccount member(Fixture fixture, String roleSlug) {
        UserAccount account = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(java.util.Locale.ROOT)));
        fixtures.addMember(fixture.workspaceId(), account.id(), roleSlug);
        return account;
    }

    private static Map<String, Object> body(Map<String, Object> given) {
        return new HashMap<>(given);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createProject(
            Fixture fixture, Map<String, Object> requestBody) {
        return post(projectsPath(fixture))
                .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(requestBody));
    }

    private UUID projectId(Fixture fixture, Map<String, Object> requestBody) throws Exception {
        String response = mockMvc.perform(createProject(fixture, body(requestBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private UUID teamId(Fixture fixture, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/teams")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions moveTo(Fixture fixture, UUID projectId, String status)
            throws Exception {
        return mockMvc.perform(post(projectPath(fixture, projectId) + "/status")
                .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status))));
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
