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
 * Which numbers each person can see, and the reason this is the phase's most important test.
 *
 * <p>The same role {@code ProjectVisibilityIT} and {@code TaskVisibilityIT} play, one level up and
 * for a sharper reason. A mistake in a listing's read scope shows somebody a row they should not
 * have; a mistake in a report's read scope changes a number. It returns 200, it looks entirely
 * reasonable, and it silently discloses the shape of a workspace: how much work there is, how late it
 * is, and how many people are carrying it.
 *
 * <p>Every case below holds identical permissions and differs only in the caller's relationship to
 * the work. A report never has its own scope; it inherits the project scope the task listing already
 * uses, which is what stops the two from drifting apart.
 *
 * <p>The last three cases are the ones that only exist here: a filter must narrow a figure and must
 * never widen it. A {@code projectId}, {@code teamId} or {@code assigneeUserId} that reaches outside
 * the caller's scope has to answer 404 or zero, never a count of work they cannot see.
 */
class ReportVisibilityIT extends ReportApiTestBase {

    @Test
    void anEmployeeOnNoProjectCountsNothing() throws Exception {
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");
        taskDue(scenario, "Not theirs", null, LocalDate.now().minusDays(1));
        taskDue(scenario, "Also not theirs", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue")).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(reportPath(scenario, "/workload")).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(reportPath(scenario, "/projects")).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void beingOnAProjectCountsThatProjectAndNoOther() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());

        taskDue(scenario, "Counted", null, LocalDate.now().minusDays(1));
        taskDue(scenario, elsewhere.id(), "Not counted", null, LocalDate.now().minusDays(1));
        taskDue(scenario, elsewhere.id(), "Also not counted", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue")).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void owningAProjectCountsIt() throws Exception {
        Scenario scenario = scenario();
        UUID owner = member(scenario, "EMPLOYEE");
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), owner, null, scenario.adminId());
        taskDue(scenario, theirs.id(), "Theirs", null, LocalDate.now().minusDays(1));
        taskDue(scenario, "Somebody else's", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution")).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void leadingATeamCountsThatTeamsProjects() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());

        taskDue(scenario, theirs.id(), "The team's", null, LocalDate.now().minusDays(1));
        taskDue(scenario, "Not the team's", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution")).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void beingOnATeamWithoutLeadingItCountsNothing() throws Exception {
        // Membership of a team is not reach over its projects. Only leading it is,
        // which is the same rule the project listing applies.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID bystander = member(scenario, "EMPLOYEE");
        var team = taskFixtures.team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId());
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team.id(), scenario.adminId());
        taskDue(scenario, theirs.id(), "The team's", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(bystander)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void holdingTheWorkspaceWideGrantCountsEverything() throws Exception {
        Scenario scenario = scenario();
        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskDue(scenario, "Here", null, LocalDate.now().minusDays(1));
        taskDue(scenario, elsewhere.id(), "There", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void aProjectFilterCannotWidenAFigureBeyondTheCallersReach() throws Exception {
        // The case that only exists in this phase. Narrowing by a project the caller
        // cannot see must refuse, not answer.
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");
        taskDue(scenario, "Out of reach", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?projectId=" + scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?projectId=" + scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(reportPath(scenario, "/trends") + "?projectId=" + scenario.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeamFilterCannotWidenAFigureBeyondTheCallersReach() throws Exception {
        // The team is visible, because team:read is held by everybody. Its projects
        // are not, and the intersection is what decides the figure.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID outsider = member(scenario, "EMPLOYEE");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();
        ProjectResponse theirs =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());
        taskDue(scenario, theirs.id(), "The team's", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?teamId=" + team)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anAssigneeFilterCannotWidenAFigureBeyondTheCallersReach() throws Exception {
        // The filter narrows within the caller's scope, so it can only return rows
        // they could already have listed. Naming somebody who works elsewhere
        // discloses nothing about what that person is carrying.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID elsewhereMember = member(scenario, "EMPLOYEE");
        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskFixtures.addProjectMember(scenario.workspaceId(), elsewhere.id(), elsewhereMember, scenario.adminId());
        taskDue(scenario, elsewhere.id(), "Out of reach", elsewhereMember, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution") + "?assigneeUserId=" + elsewhereMember)
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?assigneeUserId=" + elsewhereMember)
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aWorkloadReportShowsOnlyPeopleHoldingWorkTheCallerCanReach() throws Exception {
        // A count of colleagues is itself a disclosure. Somebody who reaches one
        // project should learn about the people on it and nobody else.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID stranger = member(scenario, "EMPLOYEE");
        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskFixtures.addProjectMember(scenario.workspaceId(), elsewhere.id(), stranger, scenario.adminId());

        taskDue(scenario, "Theirs", employee, null);
        taskDue(scenario, elsewhere.id(), "Somebody else's", stranger, null);

        mockMvc.perform(get(reportPath(scenario, "/workload")).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(employee.toString()));
    }

    @Test
    void noReportCountsAnythingFromAnotherWorkspace() throws Exception {
        Scenario mine = scenario();
        Scenario theirs = scenario();
        taskDue(theirs, "Theirs", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(mine, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(reportPath(mine, "/tasks/overdue")).header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aTrendCountsOnlyWhatTheCallerReaches() throws Exception {
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");
        taskDue(scenario, "Out of reach", null, null);

        mockMvc.perform(get(reportPath(scenario, "/trends")).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.created > 0)]").isEmpty());
    }
}
