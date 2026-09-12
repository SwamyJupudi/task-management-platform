package com.company.taskmanagementplatform.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;

/**
 * The filters, each alone and combined.
 *
 * <p>The case worth writing down is a team with no projects. A filter that matched nothing has to
 * return nothing; the failure mode is the opposite, where an empty list of identifiers turns into an
 * absent predicate and the report quietly returns everything. That is the defect the task listing's
 * impossible-identifier trick already guards against, and this is where the reports inherit it.
 */
class ReportFilteringIT extends ReportApiTestBase {

    @Test
    void narrowsADistributionToOneProject() throws Exception {
        Scenario scenario = scenario();
        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());

        taskDue(scenario, "Here", null, null);
        taskDue(scenario, elsewhere.id(), "There", null, null);
        taskDue(scenario, elsewhere.id(), "Also there", null, null);

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?projectId=" + scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void narrowsADistributionToOneTeam() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());

        taskDue(scenario, "Outside the team", null, null);
        taskDue(scenario, theirs.id(), "Inside the team", null, null);

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?teamId=" + team)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aTeamWithNoProjectsMatchesNothingRatherThanEverything() throws Exception {
        // The one to keep. An empty list of project identifiers must narrow to
        // nothing; the failure mode returns the whole workspace and looks fine.
        Scenario scenario = scenario();
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), null, scenario.adminId())
                .id();
        taskDue(scenario, "Somewhere else", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?teamId=" + team)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?teamId=" + team)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(reportPath(scenario, "/workload") + "?teamId=" + team)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void combinesAProjectAndATeamAsAnIntersection() throws Exception {
        // Two filters narrow, they never widen. A project outside the named team
        // leaves nothing, rather than leaving the project.
        Scenario scenario = scenario();
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), null, scenario.adminId())
                .id();
        taskDue(scenario, "Here", null, null);

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?projectId=" + scenario.projectId()
                                + "&teamId=" + team)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void narrowsToOnePersonWithoutWideningBeyondTheCallersReach() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID colleague = projectMember(scenario, "EMPLOYEE");

        taskDue(scenario, "Theirs", employee, null);
        taskDue(scenario, "The other one's", colleague, null);

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?assigneeUserId=" + employee)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void anAssigneeWhoMatchesNothingYieldsZeroRatherThanAnError() throws Exception {
        // Not validated against the workspace on purpose. An identifier matching
        // nothing inside the caller's scope discloses nothing, and refusing it would
        // turn the filter into a way to test whether a person exists.
        Scenario scenario = scenario();
        taskDue(scenario, "Somebody's", null, null);

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?assigneeUserId=" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aProjectTheCallerCannotSeeAnswersNotFoundRatherThanAnEmptyResult() throws Exception {
        // An empty page would say the project exists and happens to be empty, which
        // is a different and untrue statement.
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?projectId=" + scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProjectFromAnotherWorkspaceAnswersNotFound() throws Exception {
        Scenario mine = scenario();
        Scenario theirs = scenario();

        mockMvc.perform(get(reportPath(mine, "/tasks/overdue") + "?projectId=" + theirs.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeamThatDoesNotExistAnswersNotFound() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/workload") + "?teamId=" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeamThatDoesNotExistAnswersNotFoundOnTheProjectReportToo() throws Exception {
        // The project report takes the same teamId every other report takes, so it
        // has to mean the same thing. An empty page here would say the team exists
        // and happens to run nothing, which is a different statement and untrue.
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/projects") + "?teamId=" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void narrowsTheProjectReportToOneTeam() throws Exception {
        Scenario scenario = scenario();
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), null, scenario.adminId())
                .id();
        taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId(), team, scenario.adminId());

        mockMvc.perform(get(reportPath(scenario, "/projects") + "?teamId=" + team)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtersTheProjectReportByStatusOwnerAndTeam() throws Exception {
        Scenario scenario = scenario();
        UUID owner = member(scenario, "EMPLOYEE");
        taskFixtures.project(scenario.workspaceId(), uniqueKey(), owner, null, scenario.adminId());

        mockMvc.perform(get(reportPath(scenario, "/projects") + "?ownerUserId=" + owner)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(reportPath(scenario, "/projects") + "?status=PLANNING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(reportPath(scenario, "/projects") + "?status=COMPLETED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void refusesAStatusTheProjectListingWouldAlsoRefuse() throws Exception {
        // The same sentence in both places. A report that accepted a value the
        // listing rejects would be a second vocabulary for one field.
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/projects") + "?status=NOT_A_STATUS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void windowsADistributionByWhenWorkWasTakenOn() throws Exception {
        // The window is over creation. "How is the work we took on spread" is the
        // question a status breakdown answers.
        Scenario scenario = scenario();
        taskDue(scenario, "Raised today", null, null);

        LocalDate today = LocalDate.now();

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?from=" + today + "&to=" + today)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?from=" + today.minusDays(20) + "&to="
                                + today.minusDays(10))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void refusesAWindowThatEndsBeforeItStarts() throws Exception {
        Scenario scenario = scenario();
        LocalDate today = LocalDate.now();

        mockMvc.perform(get(reportPath(scenario, "/workload") + "?from=" + today + "&to=" + today.minusDays(3))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAWindowWiderThanTheConfiguredMaximum() throws Exception {
        // Forty days in the test profile, so this needs forty-one rather than a year.
        Scenario scenario = scenario();
        LocalDate today = LocalDate.now();

        mockMvc.perform(get(reportPath(scenario, "/workload") + "?from=" + today.minusDays(60) + "&to=" + today)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }
}
