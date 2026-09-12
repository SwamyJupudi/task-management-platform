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
 * Every figure the requirements name on the administrator's dashboard, against a fixture whose shape
 * is known exactly.
 *
 * <p>Each workspace here is built fresh, so the numbers are absolute rather than relative. A test
 * that asserted "at least three projects" would pass for the rest of the product's life without ever
 * checking anything again.
 */
class AdminDashboardIT extends ReportApiTestBase {

    @Test
    void countsEverybodyOnTheRosterAndSplitsThemByRole() throws Exception {
        Scenario scenario = scenario();
        member(scenario, "EMPLOYEE");
        member(scenario, "EMPLOYEE");
        member(scenario, "TEAM_LEAD");

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                // The administrator plus three. The platform account that created the
                // workspace is deliberately not a member of it.
                .andExpect(jsonPath("$.totalMembers").value(4))
                .andExpect(jsonPath("$.membersByRole.ADMIN").value(1))
                .andExpect(jsonPath("$.membersByRole.EMPLOYEE").value(2))
                .andExpect(jsonPath("$.membersByRole.TEAM_LEAD").value(1));
    }

    @Test
    void namesEveryRoleInTheSplitIncludingTheOnesNobodyHolds() throws Exception {
        // A fresh workspace holds one administrator and nobody else, so two of the
        // three seeded roles are empty. They still have to appear: a client drawing
        // a breakdown needs the whole axis, and a column that vanished when its last
        // holder left would read as a role that was never there.
        Scenario scenario = scenario();

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersByRole.ADMIN").value(1))
                .andExpect(jsonPath("$.membersByRole.EMPLOYEE").value(0))
                .andExpect(jsonPath("$.membersByRole.TEAM_LEAD").value(0));
    }

    @Test
    void countsLiveTeams() throws Exception {
        Scenario scenario = scenario();
        taskFixtures.team(scenario.workspaceId(), uniqueTeamName(), null, scenario.adminId());
        taskFixtures.team(scenario.workspaceId(), uniqueTeamName(), null, scenario.adminId());

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTeams").value(2));
    }

    @Test
    void separatesActiveProjectsFromCompletedOnes() throws Exception {
        Scenario scenario = scenario();
        taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                // Projects open in PLANNING, so neither figure counts them yet, which
                // is itself worth pinning: "active" is a status and not a synonym for
                // "not archived".
                .andExpect(jsonPath("$.activeProjects").value(0))
                .andExpect(jsonPath("$.completedProjects").value(0))
                .andExpect(jsonPath("$.projectProgress.length()").value(2));
    }

    @Test
    void countsOpenAndOverdueTasksAcrossTheWholeWorkspace() throws Exception {
        Scenario scenario = scenario();
        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        LocalDate yesterday = LocalDate.now().minusDays(1);

        taskDue(scenario, "Open here", null, null);
        taskDue(scenario, "Late here", null, yesterday);
        taskDue(scenario, elsewhere.id(), "Late there", null, yesterday);
        TaskResponse finished = taskDue(scenario, elsewhere.id(), "Finished late", null, yesterday);
        done(scenario, finished);

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTasks").value(3))
                // Finished work is never overdue, however late it was.
                .andExpect(jsonPath("$.overdueTasks").value(2));
    }

    @Test
    void breaksTheWholeWorkspaceDownByStatusAndByPriority() throws Exception {
        Scenario scenario = scenario();
        TaskResponse first = taskDue(scenario, "One", null, null);
        taskDue(scenario, "Two", null, null);
        done(scenario, first);

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskDistribution.total").value(2))
                .andExpect(jsonPath("$.taskDistribution.byStatus[?(@.key == 'DONE')].count").value(1))
                .andExpect(jsonPath("$.taskDistribution.byStatus[?(@.key == 'TODO')].count").value(1))
                // Tasks default to MEDIUM, so the whole workspace lands in one column.
                .andExpect(jsonPath("$.taskDistribution.byPriority[?(@.key == 'MEDIUM')].count").value(2));
    }

    @Test
    void showsEachProjectsDerivedProgressBesideItsTaskCounts() throws Exception {
        Scenario scenario = scenario();
        TaskResponse first = taskDue(scenario, "One", null, null);
        taskDue(scenario, "Two", null, null);
        done(scenario, first);

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectProgress[0].totalTasks").value(2))
                .andExpect(jsonPath("$.projectProgress[0].doneTasks").value(1))
                .andExpect(jsonPath("$.projectProgress[0].progress").value(50));
    }

    @Test
    void reportsTeamPerformanceOverTheProjectsEachTeamRuns() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());

        TaskResponse finished = taskDue(scenario, theirs.id(), "Finished", null, null);
        taskDue(scenario, theirs.id(), "Open", null, null);
        taskDue(scenario, theirs.id(), "Late", null, LocalDate.now().minusDays(2));
        done(scenario, finished);

        // The project outside the team must not reach its figures.
        taskDue(scenario, "Not the team's", null, LocalDate.now().minusDays(2));

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamPerformance.length()").value(1))
                .andExpect(jsonPath("$.teamPerformance[0].teamId").value(team.toString()))
                .andExpect(jsonPath("$.teamPerformance[0].leadUserId").value(lead.toString()))
                .andExpect(jsonPath("$.teamPerformance[0].projectCount").value(1))
                .andExpect(jsonPath("$.teamPerformance[0].openTasks").value(2))
                .andExpect(jsonPath("$.teamPerformance[0].overdueTasks").value(1))
                .andExpect(jsonPath("$.teamPerformance[0].completedTasks").value(1))
                .andExpect(jsonPath("$.teamPerformance[0].averageProgress").value(33));
    }

    @Test
    void countsATeamsRoster() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamPerformance[0].memberCount").value(1))
                .andExpect(jsonPath("$.teamPerformance[0].teamId").value(team.toString()));
    }

    @Test
    void readsZerosForATeamRunningNoProjects() throws Exception {
        Scenario scenario = scenario();
        taskFixtures.team(scenario.workspaceId(), uniqueTeamName(), null, scenario.adminId());

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamPerformance[0].projectCount").value(0))
                .andExpect(jsonPath("$.teamPerformance[0].openTasks").value(0))
                .andExpect(jsonPath("$.teamPerformance[0].averageProgress").value(0));
    }

    @Test
    void answersWithZerosForAnEmptyWorkspaceRatherThanWithNulls() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(get(workspaceDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTasks").value(0))
                .andExpect(jsonPath("$.overdueTasks").value(0))
                .andExpect(jsonPath("$.taskDistribution.total").value(0))
                .andExpect(jsonPath("$.taskDistribution.byStatus.length()").value(4))
                .andExpect(jsonPath("$.teamPerformance.length()").value(0));
    }

    @Test
    void countsNothingFromAnotherWorkspace() throws Exception {
        // The tenancy rule, checked where it would be least visible if broken: a
        // figure that included another company's work would still look plausible.
        Scenario mine = scenario();
        Scenario theirs = scenario();
        taskDue(theirs, "Theirs", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(workspaceDashboard(mine)).header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTasks").value(0))
                .andExpect(jsonPath("$.overdueTasks").value(0))
                .andExpect(jsonPath("$.projectProgress.length()").value(1));
    }
}
