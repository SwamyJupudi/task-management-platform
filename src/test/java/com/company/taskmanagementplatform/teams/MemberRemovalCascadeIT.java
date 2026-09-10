package com.company.taskmanagementplatform.teams;

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
 * What happens to teams when the person underneath them goes away.
 *
 * <p>Team membership and team leadership are foreign keys into {@code workspace_members}, so this is
 * not cosmetic tidying: without the cleanup the removal is a write the database refuses outright.
 * {@code TeamSchemaIT} proves the refusal; this proves the cleanup that avoids it, through the API
 * and the events rather than by calling the listener directly.
 */
class MemberRemovalCascadeIT extends AbstractIntegrationTest {

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
    void removingAWorkspaceMemberTakesThemOutOfItsTeams() throws Exception {
        Fixture fixture = workspace();
        UserAccount person = member(fixture, "EMPLOYEE");
        UUID teamId = teamId(fixture, "Roster");
        addToTeam(fixture, teamId, person.id());

        assertThat(teamMemberships(person.id())).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(teamMemberships(person.id())).isZero();
    }

    @Test
    void removingATeamLeadFromTheWorkspaceLeavesTheTeamWithoutOne() throws Exception {
        // The team survives. Choosing a replacement is not a decision the cleanup
        // is in a position to make, so it clears the column and stops there.
        Fixture fixture = workspace();
        UserAccount lead = member(fixture, "TEAM_LEAD");
        UUID teamId = teamIdWithLead(fixture, "Led", lead.id());

        assertThat(leadOf(teamId)).isEqualTo(lead.id());

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + lead.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(leadOf(teamId)).isNull();
        assertThat(teamStillExists(teamId)).isTrue();
        assertThat(teamMemberships(lead.id())).isZero();
    }

    @Test
    void deletingAnAccountClearsItsTeamStateEverywhere() throws Exception {
        // Across workspaces, because the membership cleanup removes every roster in
        // one statement and anything narrower would leave a row behind.
        Fixture first = workspace();
        Fixture second = workspace();
        UserAccount person = fixtures.verifiedUser(uniqueEmail("everywhere"));
        fixtures.addMember(first.workspaceId(), person.id(), "TEAM_LEAD");
        fixtures.addMember(second.workspaceId(), person.id(), "TEAM_LEAD");

        UUID firstTeam = teamIdWithLead(first, "First team", person.id());
        UUID secondTeam = teamIdWithLead(second, "Second team", person.id());

        users.softDelete(person.id());

        assertThat(leadOf(firstTeam)).isNull();
        assertThat(leadOf(secondTeam)).isNull();
        assertThat(teamMemberships(person.id())).isZero();
        assertThat(workspaceMemberships(person.id())).isZero();
    }

    @Test
    void theTeamsOfOtherPeopleAreUntouched() throws Exception {
        Fixture fixture = workspace();
        UserAccount leaving = member(fixture, "EMPLOYEE");
        UserAccount staying = member(fixture, "EMPLOYEE");
        UUID teamId = teamId(fixture, "Shared");
        addToTeam(fixture, teamId, leaving.id());
        addToTeam(fixture, teamId, staying.id());

        mockMvc.perform(delete("/api/v1/workspaces/" + fixture.workspaceId() + "/members/" + leaving.id())
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(teamMemberships(staying.id())).isEqualTo(1);
        assertThat(teamMemberships(leaving.id())).isZero();
    }

    @Test
    void aMembershipInAnotherWorkspaceSurvivesRemovalFromThisOne() throws Exception {
        Fixture first = workspace();
        Fixture second = workspace();
        UserAccount person = fixtures.verifiedUser(uniqueEmail("dual"));
        fixtures.addMember(first.workspaceId(), person.id(), "EMPLOYEE");
        fixtures.addMember(second.workspaceId(), person.id(), "EMPLOYEE");

        UUID firstTeam = teamId(first, "First team");
        UUID secondTeam = teamId(second, "Second team");
        addToTeam(first, firstTeam, person.id());
        addToTeam(second, secondTeam, person.id());

        mockMvc.perform(delete("/api/v1/workspaces/" + first.workspaceId() + "/members/" + person.id())
                        .header(HttpHeaders.AUTHORIZATION, first.adminToken()))
                .andExpect(status().isNoContent());

        // Only the workspace they left, not the other one.
        assertThat(teamMemberships(person.id())).isEqualTo(1);
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

    private UUID teamId(Fixture fixture, String name) throws Exception {
        return createTeam(fixture, Map.of("name", name));
    }

    private UUID teamIdWithLead(Fixture fixture, String name, UUID leadUserId) throws Exception {
        return createTeam(fixture, Map.of("name", name, "leadUserId", leadUserId.toString()));
    }

    private UUID createTeam(Fixture fixture, Map<String, String> body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/teams")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private void addToTeam(Fixture fixture, UUID teamId, UUID userId) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/" + fixture.workspaceId() + "/teams/" + teamId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", userId.toString()))))
                .andExpect(status().isCreated());
    }

    private Integer teamMemberships(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM team_members WHERE user_id = ?", Integer.class, userId);
    }

    private Integer workspaceMemberships(UUID userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM workspace_members WHERE user_id = ?", Integer.class, userId);
    }

    private UUID leadOf(UUID teamId) {
        return jdbc.queryForObject("SELECT lead_user_id FROM teams WHERE id = ?", UUID.class, teamId);
    }

    private boolean teamStillExists(UUID teamId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM teams WHERE id = ? AND deleted_at IS NULL", Integer.class, teamId);
        return count != null && count == 1;
    }

    private record Fixture(UUID workspaceId, String adminToken) {}
}
