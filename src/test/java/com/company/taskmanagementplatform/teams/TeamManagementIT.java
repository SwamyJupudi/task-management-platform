package com.company.taskmanagementplatform.teams;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
 * Creating teams, editing them, archiving them, and moving people in and out.
 *
 * <p>Driven entirely through the API, because these are the paths a client actually takes and the
 * rules worth protecting are the ones a request can reach.
 */
class TeamManagementIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anAdministratorCreatesATeam() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(createTeam(fixture, Map.of("name", "Platform", "description", "Keeps it running")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Platform"))
                .andExpect(jsonPath("$.description").value("Keeps it running"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.memberCount").value(0))
                .andExpect(jsonPath("$.leadUserId").doesNotExist());
    }

    @Test
    void namingALeadAtCreationAlsoPutsThemInTheTeam() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");

        mockMvc.perform(createTeam(fixture, Map.of("name", "Led", "leadUserId", lead.id().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leadUserId").value(lead.id().toString()))
                .andExpect(jsonPath("$.leadEmail").value(lead.email()))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void refusesALeadWhoIsNotInTheWorkspace() throws Exception {
        Fixture fixture = workspace();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));

        mockMvc.perform(createTeam(fixture, Map.of("name", "Impossible", "leadUserId", outsider.id().toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesASecondTeamWithTheSameNameHoweverItIsCased() throws Exception {
        Fixture fixture = workspace();
        mockMvc.perform(createTeam(fixture, Map.of("name", "Platform"))).andExpect(status().isCreated());

        mockMvc.perform(createTeam(fixture, Map.of("name", "  platform  ")))
                .andExpect(status().isConflict());
    }

    @Test
    void theSameNameIsFreeInAnotherWorkspace() throws Exception {
        Fixture mine = workspace();
        Fixture theirs = workspace();

        mockMvc.perform(createTeam(mine, Map.of("name", "Platform"))).andExpect(status().isCreated());
        mockMvc.perform(createTeam(theirs, Map.of("name", "Platform"))).andExpect(status().isCreated());
    }

    @Test
    void editingATeamLeavesOmittedFieldsAlone() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, Map.of("name", "Before", "description", "Kept"));

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "After"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.description").value("Kept"));
    }

    @Test
    void renamingOntoAnotherTeamsNameIsAConflict() throws Exception {
        Fixture fixture = workspace();
        mockMvc.perform(createTeam(fixture, Map.of("name", "Taken"))).andExpect(status().isCreated());
        UUID teamId = teamId(fixture, Map.of("name", "Free"));

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Taken"))))
                .andExpect(status().isConflict());
    }

    @Test
    void renamingATeamToItsOwnNameIsAllowed() throws Exception {
        // The uniqueness check has to exclude the row being edited, or editing a
        // description would fail whenever the name was sent along with it.
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, Map.of("name", "Steady"));

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Steady", "description", "New words"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("New words"));
    }

    @Test
    void archivingATeamFreezesItAndRestoringUnfreezesIt() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID teamId = teamId(fixture, Map.of("name", "Seasonal"));

        mockMvc.perform(post(teamPath(fixture, teamId) + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(post(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", person.id().toString()))))
                .andExpect(status().isConflict());

        // Still readable while frozen.
        mockMvc.perform(get(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post(teamPath(fixture, teamId) + "/unarchive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", person.id().toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void archivingTwiceIsAConflict() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, Map.of("name", "Once"));

        mockMvc.perform(post(teamPath(fixture, teamId) + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post(teamPath(fixture, teamId) + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingATeamHidesItAndFreesItsName() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, Map.of("name", "Temporary"));

        mockMvc.perform(delete(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(createTeam(fixture, Map.of("name", "Temporary"))).andExpect(status().isCreated());
    }

    @Test
    void listingFiltersByStatusAndRejectsAnUnknownOne() throws Exception {
        Fixture fixture = workspace();
        UUID archived = teamId(fixture, Map.of("name", "Old"));
        mockMvc.perform(createTeam(fixture, Map.of("name", "Current"))).andExpect(status().isCreated());
        mockMvc.perform(post(teamPath(fixture, archived) + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get(teamsPath(fixture))
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Current"));

        mockMvc.perform(get(teamsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get(teamsPath(fixture))
                        .param("status", "RETIRED")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membersAreAddedListedAndRemoved() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID teamId = teamId(fixture, Map.of("name", "Roster"));

        mockMvc.perform(post(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", person.id().toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(person.email()))
                .andExpect(jsonPath("$.lead").value(false));

        mockMvc.perform(get(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete(teamPath(fixture, teamId) + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void refusesAMemberWhoDoesNotBelongToTheWorkspace() throws Exception {
        Fixture fixture = workspace();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));
        UUID teamId = teamId(fixture, Map.of("name", "Closed"));

        mockMvc.perform(post(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", outsider.id().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesTheSamePersonTwice() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID teamId = teamId(fixture, Map.of("name", "Once only"));

        addMember(fixture, teamId, person.id());

        mockMvc.perform(post(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", person.id().toString()))))
                .andExpect(status().isConflict());
    }

    @Test
    void assigningALeadAddsThemToTheTeamIfTheyAreNotInIt() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID teamId = teamId(fixture, Map.of("name", "Leaderless"));

        mockMvc.perform(put(teamPath(fixture, teamId) + "/lead")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", lead.id().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadUserId").value(lead.id().toString()))
                .andExpect(jsonPath("$.memberCount").value(1));

        mockMvc.perform(get(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lead").value(true));
    }

    @Test
    void removingTheLeadFromTheirOwnTeamIsRefused() throws Exception {
        // Losing a team's lead should be a decision, not a side effect of tidying
        // the roster.
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID teamId = teamId(fixture, Map.of("name", "Led", "leadUserId", lead.id().toString()));

        mockMvc.perform(delete(teamPath(fixture, teamId) + "/members/" + lead.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void clearingTheLeadLeavesThemInTheTeam() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID teamId = teamId(fixture, Map.of("name", "Standing down", "leadUserId", lead.id().toString()));

        mockMvc.perform(delete(teamPath(fixture, teamId) + "/lead")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadUserId").doesNotExist())
                .andExpect(jsonPath("$.memberCount").value(1));

        // And now they can be removed from the roster like anybody else.
        mockMvc.perform(delete(teamPath(fixture, teamId) + "/members/" + lead.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void clearingALeadThatIsNotThereIsAConflict() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, Map.of("name", "Never led"));

        mockMvc.perform(delete(teamPath(fixture, teamId) + "/lead")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesATeamWithNoName() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(createTeam(fixture, Map.of("name", "   ")))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Teams", uniqueSlug("teams"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");
        return new Fixture(workspace.id(), fixtures.bearer(admin.id()));
    }

    private UserAccount member(Fixture fixture, String roleSlug) {
        UserAccount account = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(java.util.Locale.ROOT)));
        fixtures.addMember(fixture.workspaceId(), account.id(), roleSlug);
        return account;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createTeam(
            Fixture fixture, Map<String, String> body) throws Exception {
        return post(teamsPath(fixture))
                .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new HashMap<>(body)));
    }

    private UUID teamId(Fixture fixture, Map<String, String> body) throws Exception {
        String response = mockMvc.perform(createTeam(fixture, body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private void addMember(Fixture fixture, UUID teamId, UUID userId) throws Exception {
        mockMvc.perform(post(teamPath(fixture, teamId) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", userId.toString()))))
                .andExpect(status().isCreated());
    }

    private static String teamsPath(Fixture fixture) {
        return "/api/v1/workspaces/" + fixture.workspaceId() + "/teams";
    }

    private static String teamPath(Fixture fixture, UUID teamId) {
        return teamsPath(fixture) + "/" + teamId;
    }

    private record Fixture(UUID workspaceId, String adminToken) {}
}
