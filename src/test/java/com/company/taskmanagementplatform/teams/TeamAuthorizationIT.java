package com.company.taskmanagementplatform.teams;

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
 * Who may do what to a team, and to which teams.
 *
 * <p>This is the phase's most important test, because it exercises the two-layer rule rather than
 * assuming it. Permission answers what a caller may do; scope answers which rows they may do it to. A
 * team lead passes the first for every team in the workspace and the second for only their own, and
 * the difference between those two facts is the whole point of {@code team:manage_any}.
 */
class TeamAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anEmployeeMaySeeTeamsButNotCreateOrChangeThem() throws Exception {
        Fixture fixture = workspace();
        UserAccount employee = member(fixture, "EMPLOYEE");
        UUID teamId = teamId(fixture, "Visible", null);
        String token = fixtures.bearer(employee.id());

        mockMvc.perform(get(teamsPath(fixture)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(get(teamPath(fixture, teamId)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        mockMvc.perform(post(teamsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Mine"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Renamed"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(teamPath(fixture, teamId)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeamLeadMayChangeTheTeamTheyLead() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID teamId = teamId(fixture, "Theirs", lead.id());
        String token = fixtures.bearer(lead.id());

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "We ship things"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("We ship things"));
    }

    @Test
    void aTeamLeadMayNotChangeATeamTheyDoNotLead() throws Exception {
        // Same permission, different row. This is the scope layer doing its job.
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID theirs = teamId(fixture, "Theirs", lead.id());
        UUID somebodyElses = teamId(fixture, "Somebody elses", null);
        String token = fixtures.bearer(lead.id());

        mockMvc.perform(patch(teamPath(fixture, theirs))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Fine"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch(teamPath(fixture, somebodyElses))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Not fine"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    void aTeamLeadManagesTheRosterOfTheirOwnTeamOnly() throws Exception {
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UserAccount employee = member(fixture, "EMPLOYEE");
        UUID theirs = teamId(fixture, "Theirs", lead.id());
        UUID somebodyElses = teamId(fixture, "Somebody elses", null);
        String token = fixtures.bearer(lead.id());

        mockMvc.perform(post(teamPath(fixture, theirs) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", employee.id().toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(teamPath(fixture, somebodyElses) + "/members")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", employee.id().toString()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(teamPath(fixture, somebodyElses) + "/lead")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", employee.id().toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeamLeadMayNotCreateOrDeleteATeamEvenTheirOwn() throws Exception {
        // team:create and team:delete are the administrator's, and team:manage_any
        // would not help here because neither code is granted to the lead at all.
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID theirs = teamId(fixture, "Theirs", lead.id());
        String token = fixtures.bearer(lead.id());

        mockMvc.perform(post(teamsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "A new one"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(teamPath(fixture, theirs)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorReachesEveryTeamInTheWorkspace() throws Exception {
        // Because they hold team:manage_any, which is what widens the same two
        // codes the lead holds from "mine" to "all of them".
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID somebodyElses = teamId(fixture, "Led by another", lead.id());

        mockMvc.perform(patch(teamPath(fixture, somebodyElses))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Administered"))))
                .andExpect(status().isOk());
    }

    @Test
    void aStrangerSeesTheTeamsOfAWorkspaceAsMissing() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, "Private", null);
        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));
        String token = fixtures.bearer(stranger.id());

        mockMvc.perform(get(teamsPath(fixture)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));

        mockMvc.perform(get(teamPath(fixture, teamId)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeamFromAnotherWorkspaceIsMissingRatherThanForbidden() throws Exception {
        // Addressed through a workspace the caller does belong to, so the 404 comes
        // from the team lookup being scoped rather than from the workspace guard.
        Fixture mine = workspace();
        Fixture theirs = workspace();
        UUID foreignTeam = teamId(theirs, "Elsewhere", null);

        mockMvc.perform(get(teamPath(mine, foreignTeam)).header(HttpHeaders.AUTHORIZATION, mine.adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));

        mockMvc.perform(patch(teamPath(mine, foreignTeam))
                        .header(HttpHeaders.AUTHORIZATION, mine.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Reached across"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeamThatDoesNotExistAnswersTheSameWay() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(get(teamPath(fixture, UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePlatformAdministratorReachesTeamsWithoutBelongingToTheWorkspace() throws Exception {
        Fixture fixture = workspace();
        UUID teamId = teamId(fixture, "Any", null);

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(fixture.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "By the platform"))))
                .andExpect(status().isOk());
    }

    @Test
    void aRoleChangeChangesWhatSomebodyMayDoToATeamAtOnce() throws Exception {
        // Nothing about the permission set is cached against the token.
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID teamId = teamId(fixture, "Promotion", null);
        String token = fixtures.bearer(person.id());

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Too soon"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("roleSlug", "ADMIN"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch(teamPath(fixture, teamId))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Now allowed"))))
                .andExpect(status().isOk());
    }

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        Fixture fixture = workspace();

        mockMvc.perform(get(teamsPath(fixture))).andExpect(status().isUnauthorized());
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Teams", uniqueSlug("teams"), owner.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");
        return new Fixture(workspace.id(), owner.id(), fixtures.bearer(admin.id()));
    }

    private UserAccount member(Fixture fixture, String roleSlug) {
        UserAccount account = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(java.util.Locale.ROOT)));
        fixtures.addMember(fixture.workspaceId(), account.id(), roleSlug);
        return account;
    }

    private UUID teamId(Fixture fixture, String name, UUID leadUserId) throws Exception {
        Map<String, String> body = leadUserId == null
                ? Map.of("name", name)
                : Map.of("name", name, "leadUserId", leadUserId.toString());

        String response = mockMvc.perform(post(teamsPath(fixture))
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private static String teamsPath(Fixture fixture) {
        return "/api/v1/workspaces/" + fixture.workspaceId() + "/teams";
    }

    private static String teamPath(Fixture fixture, UUID teamId) {
        return teamsPath(fixture) + "/" + teamId;
    }

    private record Fixture(UUID workspaceId, UUID ownerId, String adminToken) {}
}
