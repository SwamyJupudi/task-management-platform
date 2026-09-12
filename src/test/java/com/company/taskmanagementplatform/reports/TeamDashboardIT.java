package com.company.taskmanagementplatform.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * One team's figures, and the boundaries of what belongs to a team.
 *
 * <p>Team membership and team work are two different sets and the dashboard has to keep them
 * straight. The figures are over the projects the team runs, whoever happens to be doing the work;
 * the workload rows are over the people, whether or not they are doing any. Conflating the two would
 * make a team's numbers change every time somebody was lent out for a week.
 */
class TeamDashboardIT extends ReportApiTestBase {

    @Test
    void reportsTheTeamsOwnProjectsAndWork() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = team(scenario, lead);
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());

        TaskResponse finished = taskDue(scenario, theirs.id(), "Finished", null, null);
        taskDue(scenario, theirs.id(), "Open", null, null);
        taskDue(scenario, theirs.id(), "Late", null, LocalDate.now().minusDays(4));
        done(scenario, finished);

        mockMvc.perform(get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(team.toString()))
                .andExpect(jsonPath("$.leadUserId").value(lead.toString()))
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.openTasks").value(2))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.averageProgress").value(33));
    }

    @Test
    void leavesOutWorkInProjectsTheTeamDoesNotRun() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = team(scenario, lead);

        taskDue(scenario, "Somebody else's project", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTasks").value(0))
                .andExpect(jsonPath("$.overdueTasks").value(0))
                .andExpect(jsonPath("$.taskDistribution.total").value(0));
    }

    @Test
    void readsZerosForATeamWithNoProjectsRatherThanNotFound() throws Exception {
        // The team exists. An empty scope matches nothing rather than everything,
        // which is the distinction that keeps this from being a disclosure.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = team(scenario, lead);

        mockMvc.perform(get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(0))
                .andExpect(jsonPath("$.openTasks").value(0))
                .andExpect(jsonPath("$.averageProgress").value(0))
                .andExpect(jsonPath("$.taskDistribution.byStatus.length()").value(4));
    }

    @Test
    void listsEveryMemberIncludingThoseCarryingNothing() throws Exception {
        // Somebody with an empty desk is a fact about a team rather than a row to
        // leave out.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = team(scenario, lead);

        mockMvc.perform(get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.workload.length()").value(1))
                .andExpect(jsonPath("$.workload[0].userId").value(lead.toString()))
                .andExpect(jsonPath("$.workload[0].open").value(0));
    }

    @Test
    void countsEachPersonsOpenOverdueAndEffortOverTheTeamsProjects() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = team(scenario, lead);
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());
        taskFixtures.addProjectMember(scenario.workspaceId(), theirs.id(), lead, scenario.adminId());

        taskFixtures.task(
                scenario.workspaceId(),
                theirs.id(),
                new com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest(
                        "Estimated", null, lead, null, null, null, LocalDate.now().minusDays(1), 120, 90, null),
                scenario.adminId());

        mockMvc.perform(get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workload[0].open").value(1))
                .andExpect(jsonPath("$.workload[0].overdue").value(1))
                .andExpect(jsonPath("$.workload[0].estimatedMinutes").value(120))
                .andExpect(jsonPath("$.workload[0].actualMinutes").value(90));
    }

    @Test
    void stopsCountingAProjectOnceItMovesToAnotherTeam() throws Exception {
        // Team performance is a statement about the projects a team runs now, not
        // about what it once ran. The figure has to follow the move.
        Scenario scenario = scenario();
        UUID firstLead = member(scenario, "TEAM_LEAD");
        UUID secondLead = member(scenario, "TEAM_LEAD");
        UUID first = team(scenario, firstLead);
        UUID second = team(scenario, secondLead);

        ProjectResponse project =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, first, scenario.adminId());
        taskDue(scenario, project.id(), "Work", null, null);

        mockMvc.perform(get(teamDashboard(scenario, first)).header(HttpHeaders.AUTHORIZATION, bearer(firstLead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.openTasks").value(1));

        moveToTeam(scenario, project.id(), second);

        mockMvc.perform(get(teamDashboard(scenario, first)).header(HttpHeaders.AUTHORIZATION, bearer(firstLead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(0))
                .andExpect(jsonPath("$.openTasks").value(0));
        mockMvc.perform(get(teamDashboard(scenario, second)).header(HttpHeaders.AUTHORIZATION, bearer(secondLead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.openTasks").value(1));
    }

    @Test
    void showsTheSameNumbersToTheLeadAndToAnAdministrator() throws Exception {
        // The property the whole design decision rests on. Two people who can open
        // one team dashboard must see one team dashboard.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = team(scenario, lead);
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());
        taskDue(scenario, theirs.id(), "Work", null, LocalDate.now().minusDays(1));

        String asLead = mockMvc.perform(
                        get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String asAdmin = mockMvc.perform(
                        get(teamDashboard(scenario, team)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(asLead).isEqualTo(asAdmin);
    }

    private UUID team(Scenario scenario, UUID leadUserId) {
        return taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), leadUserId, scenario.adminId())
                .id();
    }

    /** Moves a project between teams through the ordinary update path. */
    private void moveToTeam(Scenario scenario, UUID projectId, UUID teamId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                workspacePath(scenario) + "/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isOk());
    }
}
