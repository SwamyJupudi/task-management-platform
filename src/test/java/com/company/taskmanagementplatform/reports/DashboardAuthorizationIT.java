package com.company.taskmanagementplatform.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.users.UserAccount;

/**
 * Who may open which dashboard, and what each refusal looks like.
 *
 * <p>Three different answers are correct here and telling them apart is the point:
 *
 * <ul>
 *   <li>401 for somebody with no token, produced by the entry point rather than by any of this code
 *   <li>403 for a member who can see the workspace but may not summarise the whole of it
 *   <li>404 for a workspace they have nothing to do with, and for a team out of their reach, because
 *       a 403 would confirm the identifier names something real
 * </ul>
 */
class DashboardAuthorizationIT extends ReportApiTestBase {

    @Test
    void anEmployeeReachesTheirOwnDashboard() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk());
    }

    @Test
    void anEmployeeOnNoProjectStillReachesItAndGetsZeros() throws Exception {
        // Having no work is not the same as having no business asking. They belong
        // to the workspace; there is simply nothing of theirs in it yet.
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk());
    }

    @Test
    void anEmployeeIsRefusedTheWorkspaceDashboard() throws Exception {
        // 403 rather than a narrowed dashboard. A company-wide figure computed over
        // one person's projects would be a wrong number rather than a discreet one.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeamLeadIsAlsoRefusedTheWorkspaceDashboard() throws Exception {
        // Leading a team widens which projects are in reach, not whether a
        // workspace-wide figure may be asked for. That is project:read_any, and a
        // team lead does not hold it.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorReachesBoth() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());
    }

    @Test
    void aTeamLeadReachesTheirOwnTeamsDashboard() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();

        mockMvc.perform(get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk());
    }

    @Test
    void aTeamLeadGetsNotFoundForSomebodyElsesTeam() throws Exception {
        // 404 rather than 403, matching the rule that an unreachable record is
        // reported as missing. A 403 would confirm the identifier names a real team.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID otherLead = member(scenario, "TEAM_LEAD");

        taskFixtures.team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId());
        UUID theirs = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), otherLead, scenario.adminId())
                .id();

        mockMvc.perform(get(teamDashboard(scenario, theirs)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmployeeOnTheTeamsProjectStillGetsNotFoundForItsDashboard() throws Exception {
        // Deliberately not narrowed per viewer. A partial team dashboard would hand
        // two people different numbers under the same heading, which is worse than
        // refusing one of them.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();

        mockMvc.perform(get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorReachesAnyTeamsDashboard() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();

        mockMvc.perform(
                        get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());
    }

    @Test
    void aTeamFromAnotherWorkspaceIsIndistinguishableFromOneThatNeverExisted() throws Exception {
        Scenario mine = scenario();
        Scenario theirs = scenario();
        UUID theirTeam = taskFixtures
                .team(theirs.workspaceId(), uniqueTeamName(), theirs.adminId(), theirs.adminId())
                .id();

        mockMvc.perform(get(teamDashboard(mine, theirTeam)).header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get(teamDashboard(mine, UUID.randomUUID())).header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNonMemberGetsNotFoundForTheWorkspaceItself() throws Exception {
        // Before any permission is considered. A workspace a stranger has nothing to
        // do with must look like nothing at all.
        Scenario scenario = scenario();
        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(stranger.id())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(stranger.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAnonymousCallerIsRefusedBeforeAnyOfThisRuns() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(get(myDashboard(scenario))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(workspaceDashboard(scenario))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(reportPath(scenario, "/projects"))).andExpect(status().isUnauthorized());
    }
}
