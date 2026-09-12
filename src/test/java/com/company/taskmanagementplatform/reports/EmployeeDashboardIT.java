package com.company.taskmanagementplatform.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * Every figure the requirements name on the employee dashboard, against a fixture of a known shape.
 *
 * <p>The case that earns its keep is the last one: a task in the same project, assigned to somebody
 * else, must be absent from every "my" figure. That is the difference between a dashboard narrowed by
 * assignment and one narrowed by reach, and getting it wrong produces a number that is plausible,
 * larger than it should be, and wrong in a way nobody would question.
 */
class EmployeeDashboardIT extends ReportApiTestBase {

    @Test
    void countsOnlyTheCallersOwnTasks() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID colleague = projectMember(scenario, "EMPLOYEE");

        taskDue(scenario, "Mine", employee, null);
        taskDue(scenario, "Mine too", employee, null);
        taskDue(scenario, "Theirs", colleague, null);
        taskDue(scenario, "Nobody's", null, null);

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myTaskCounts.total").value(2));
    }

    @Test
    void listsTheProjectsInReachRatherThanOnlyThoseWithWorkInThem() throws Exception {
        // The one panel narrowed by scope rather than by assignment. A project
        // somebody is on but holds nothing in is still theirs to open.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myProjects.length()").value(1))
                .andExpect(jsonPath("$.myProjects[0].key").value(scenario.projectKey()));
    }

    @Test
    void omitsProjectsTheCallerCannotReach() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myProjects.length()").value(1));
    }

    @Test
    void countsOnlyTheCallersOwnOverdueWork() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID colleague = projectMember(scenario, "EMPLOYEE");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        taskDue(scenario, "Mine and late", employee, yesterday);
        taskDue(scenario, "Theirs and late", colleague, yesterday);

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueCount").value(1));
    }

    @Test
    void showsDeadlinesInsideTheLeadWindowAndNotBeyondIt() throws Exception {
        // The window is app.reports.upcoming-lead-days, a week in the test profile.
        // A deadline a fortnight out is real and is not this week's planning.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        LocalDate today = LocalDate.now();

        taskDue(scenario, "Due in three days", employee, today.plusDays(3));
        taskDue(scenario, "Due in a fortnight", employee, today.plusDays(14));

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingDeadlines.length()").value(1))
                .andExpect(jsonPath("$.upcomingDeadlines[0].title").value("Due in three days"))
                .andExpect(jsonPath("$.upcomingDeadlines[0].daysRemaining").value(3));
    }

    @Test
    void includesWorkDueTodayInTheUpcomingPanelAtZeroDays() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        taskDue(scenario, "Due today", employee, LocalDate.now());

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingDeadlines[0].daysRemaining").value(0))
                .andExpect(jsonPath("$.overdueCount").value(0));
    }

    @Test
    void keepsLateWorkOutOfTheUpcomingPanel() throws Exception {
        // It belongs in the overdue count. Showing it in both would put the same row
        // on the screen twice under two different headings.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        taskDue(scenario, "Already late", employee, LocalDate.now().minusDays(2));

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingDeadlines.length()").value(0))
                .andExpect(jsonPath("$.overdueCount").value(1));
    }

    @Test
    void keepsFinishedWorkOutOfBothPanels() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        TaskResponse late = taskDue(scenario, "Late but finished", employee, LocalDate.now().minusDays(3));
        TaskResponse soon = taskDue(scenario, "Soon but finished", employee, LocalDate.now().plusDays(1));
        done(scenario, late);
        done(scenario, soon);

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueCount").value(0))
                .andExpect(jsonPath("$.upcomingDeadlines.length()").value(0))
                .andExpect(jsonPath("$.myTaskCounts.total").value(2));
    }

    @Test
    void countsOnlyTheCallersOwnOpenChecklistItems() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskDue(scenario, "Has a checklist", employee, null);

        taskFixtures.subtask(taskFixtures.ref(task), "Unassigned step", scenario.adminId());

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myOpenSubtasks").value(0));
    }

    @Test
    void showsTheCallersOwnRecentActivityAndNobodyElses() throws Exception {
        // Not a widening of the audit read. Every row names the caller as the actor,
        // so it discloses nothing they did not do themselves; browsing the workspace
        // is still activity:read, which only an administrator holds.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Raised by the admin", scenario.adminId());

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentActivity[?(@.actorUserId != '" + employee + "')]").isEmpty());
    }

    @Test
    void answersWithZerosRatherThanAnErrorForSomebodyWithNothing() throws Exception {
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");
        taskDue(scenario, "Not theirs", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myProjects.length()").value(0))
                .andExpect(jsonPath("$.myTaskCounts.total").value(0))
                .andExpect(jsonPath("$.overdueCount").value(0))
                .andExpect(jsonPath("$.myOpenSubtasks").value(0));
    }

    @Test
    void drawsEveryColumnOfTheBreakdownIncludingEmptyOnes() throws Exception {
        // A chart with a missing column is one the client has to know the whole enum
        // to draw.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        taskDue(scenario, "Only one", employee, null);

        mockMvc.perform(get(myDashboard(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myTaskCounts.byStatus.length()").value(4))
                .andExpect(jsonPath("$.myTaskCounts.byPriority.length()").value(4))
                .andExpect(jsonPath("$.myTaskCounts.byStatus[0].key").value("TODO"))
                .andExpect(jsonPath("$.myTaskCounts.byStatus[0].label").value("Todo"));
    }
}
