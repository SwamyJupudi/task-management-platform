package com.company.taskmanagementplatform.workspaces;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Workspace settings and the archive, restore and delete lifecycle.
 *
 * <p>The rule worth watching is that archiving freezes a workspace rather than hiding it. Everything
 * inside stays readable and nothing inside may be changed, and that has to hold for the endpoints
 * built in the identity phase as well as the ones built here.
 */
class WorkspaceLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anAdministratorEditsTheSettingsOfTheirWorkspace() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Renamed",
                                "description", "What we do",
                                "timezone", "Europe/London",
                                "defaultRoleSlug", "TEAM_LEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.description").value("What we do"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.defaultRoleSlug").value("TEAM_LEAD"));
    }

    @Test
    void anOmittedFieldIsLeftAlone() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Only this"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Only this"))
                // Untouched, and still the value it was created with.
                .andExpect(jsonPath("$.name").value("Lifecycle"))
                .andExpect(jsonPath("$.timezone").value("UTC"));
    }

    @Test
    void aBlankDescriptionClearsIt() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Something"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void refusesATimeZoneNothingKnows() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("timezone", "Mars/Olympus"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesADefaultRoleFromAnotherWorkspace() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("defaultRoleSlug", "NOT_A_ROLE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anEmployeeMayNotEditSettings() throws Exception {
        Fixture fixture = workspaceWithAdmin();
        UserAccount employee = fixtures.verifiedUser(uniqueEmail("employee"));
        fixtures.addMember(fixture.workspaceId(), employee.id(), "EMPLOYEE");

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Mine now"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anArchivedWorkspaceStaysReadable() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspaces/" + fixture.workspaceId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspaces/" + fixture.workspaceId() + "/teams")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void restoringLetsWorkContinue() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/unarchive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.archivedAt").doesNotExist());

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Back"))))
                .andExpect(status().isOk());
    }

    @Test
    void archivingTwiceIsAConflict() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void restoringOneThatWasNeverArchivedIsAConflict() throws Exception {
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/unarchive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void anEmployeeMayNotArchiveTheWorkspace() throws Exception {
        Fixture fixture = workspaceWithAdmin();
        UserAccount employee = fixtures.verifiedUser(uniqueEmail("employee"));
        fixtures.addMember(fixture.workspaceId(), employee.id(), "EMPLOYEE");

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWorkspaceAdministratorMayNotDeleteTheirOwnWorkspace() throws Exception {
        // workspace:delete is granted to no workspace role. Removing a workspace is
        // platform administration, and deliberately not something its own
        // administrator can do.
        Fixture fixture = workspaceWithAdmin();

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingHidesTheWorkspaceAndReleasesItsSlug() throws Exception {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        String slug = uniqueSlug("recycled");
        WorkspaceResponse workspace = fixtures.workspace("Recycled", slug, owner.id());
        String ownerToken = fixtures.bearer(owner.id());

        mockMvc.perform(delete("/api/v1/workspaces/" + workspace.id())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/" + workspace.id())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound());

        // The slug is free again, because the unique index is partial.
        mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Reused", "slug", slug))))
                .andExpect(status().isCreated());
    }

    @Test
    void aStrangerSeesAnArchivedWorkspaceAsMissingRatherThanFrozen() throws Exception {
        // The order of the guard's questions. A 409 here would confirm the
        // identifier names something real.
        Fixture fixture = workspaceWithAdmin();
        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));

        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId())
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(stranger.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Nope"))))
                .andExpect(status().isNotFound());
    }

    private Fixture workspaceWithAdmin() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Lifecycle", uniqueSlug("lifecycle"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");
        return new Fixture(workspace.id(), fixtures.bearer(admin.id()));
    }

    private record Fixture(java.util.UUID workspaceId, String adminToken) {}
}
