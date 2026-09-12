package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * The numbers themselves, and the boundaries they turn on.
 *
 * <p>Half of this file is about edges: a task due today, a finished task long past its date, a
 * deleted task, a deleted subtask. Every one of those is a case where a wrong figure looks entirely
 * reasonable, which is what makes a reporting feature dangerous to ship untested.
 *
 * <p>The last test is the one that guards against the defect this phase is most likely to produce
 * years from now: the overdue report and the task listing's own {@code overdue} filter drifting
 * apart, so that a count and the list it links to differ by a row and nobody can say which is right.
 */
class ReportAccuracyIT extends ReportApiTestBase {

    @Test
    void countsTasksByStatusAndByPriority() throws Exception {
        Scenario scenario = scenario();

        raise(scenario, "Critical one", "CRITICAL", null);
        raise(scenario, "Critical two", "CRITICAL", null);
        TaskResponse low = raise(scenario, "Low", "LOW", null);
        done(scenario, low);

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byPriority[?(@.key == 'CRITICAL')].count").value(2))
                .andExpect(jsonPath("$.byPriority[?(@.key == 'LOW')].count").value(1))
                .andExpect(jsonPath("$.byStatus[?(@.key == 'DONE')].count").value(1))
                .andExpect(jsonPath("$.byStatus[?(@.key == 'TODO')].count").value(2));
    }

    @Test
    void everyBreakdownSumsToItsOwnTotal() throws Exception {
        // The cheapest property that catches a whole class of grouping mistakes: if
        // a column is dropped or double counted, the two lists stop agreeing with
        // the number printed beside them.
        Scenario scenario = scenario();
        raise(scenario, "One", "HIGH", null);
        raise(scenario, "Two", "LOW", null);
        done(scenario, raise(scenario, "Three", "MEDIUM", null));

        String body = mockMvc.perform(get(reportPath(scenario, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var distribution = json.readTree(body);
        long total = distribution.get("total").asLong();

        assertThat(sum(distribution.get("byStatus"))).isEqualTo(total);
        assertThat(sum(distribution.get("byPriority"))).isEqualTo(total);
    }

    @Test
    void aTaskDueTodayIsNotOverdue() throws Exception {
        // The day is not over. The boundary the whole definition turns on.
        Scenario scenario = scenario();
        taskDue(scenario, "Due today", null, LocalDate.now());

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aFinishedTaskPastItsDateIsNotOverdue() throws Exception {
        Scenario scenario = scenario();
        done(scenario, taskDue(scenario, "Delivered late", null, LocalDate.now().minusDays(30)));

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aTaskWithNoDueDateIsNeverOverdue() throws Exception {
        Scenario scenario = scenario();
        taskDue(scenario, "Undated", null, null);

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void reportsHowManyDaysLateEachTaskIs() throws Exception {
        Scenario scenario = scenario();
        taskDue(scenario, "Six days late", null, LocalDate.now().minusDays(6));

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].daysOverdue").value(6))
                .andExpect(jsonPath("$.content[0].key").value(scenario.projectKey() + "-1"));
    }

    @Test
    void aDeletedTaskLeavesBothSidesOfEveryFraction() throws Exception {
        // Soft deletion is invisible to every figure, which is the only reading of
        // it that makes a progress percentage mean anything.
        Scenario scenario = scenario();
        TaskResponse kept = taskDue(scenario, "Kept", null, LocalDate.now().minusDays(1));
        TaskResponse removed = taskDue(scenario, "Removed", null, LocalDate.now().minusDays(1));
        done(scenario, kept);

        taskFixtures.deleteTask(scenario.workspaceId(), removed.id(), scenario.adminId());

        mockMvc.perform(get(reportPath(scenario, "/tasks/distribution"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get(reportPath(scenario, "/projects"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalTasks").value(1))
                .andExpect(jsonPath("$.content[0].progress").value(100));
    }

    @Test
    void aDeletedSubtaskLeavesTheProgressFractionToo() throws Exception {
        // Progress is derived from tasks and subtasks together, and this phase reads
        // that column rather than recomputing it. This checks the reading stays true
        // after a checklist item goes.
        Scenario scenario = scenario();
        TaskResponse task = taskDue(scenario, "Has a checklist", null, null);
        var first = taskFixtures.subtask(taskFixtures.ref(task), "One", scenario.adminId());
        taskFixtures.subtask(taskFixtures.ref(task), "Two", scenario.adminId());

        taskFixtures.moveSubtask(taskFixtures.ref(task), first.id(), "DONE", scenario.adminId());

        String before = progressOf(scenario);
        taskFixtures.deleteSubtask(taskFixtures.ref(task), first.id(), scenario.adminId());
        String after = progressOf(scenario);

        assertThat(before).isNotEqualTo(after);
        mockMvc.perform(get(reportPath(scenario, "/projects"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].progress").value(0));
    }

    @Test
    void countsWorkloadPerPersonOverOpenWorkOnly() throws Exception {
        // A person's load is what is still on their desk. Adding the minutes of
        // everything they ever finished would make the number grow forever.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        raiseFor(scenario, "Open", employee, LocalDate.now().minusDays(1), 120, 60);
        TaskResponse finished = raiseFor(scenario, "Finished", employee, null, 999, 999);
        done(scenario, finished);

        mockMvc.perform(get(reportPath(scenario, "/workload"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(employee.toString()))
                .andExpect(jsonPath("$.content[0].open").value(1))
                .andExpect(jsonPath("$.content[0].overdue").value(1))
                .andExpect(jsonPath("$.content[0].estimatedMinutes").value(120))
                .andExpect(jsonPath("$.content[0].actualMinutes").value(60));
    }

    @Test
    void leavesUnassignedWorkOutOfTheWorkloadReport() throws Exception {
        // Workload is a statement about people, and a row naming nobody is not one.
        Scenario scenario = scenario();
        taskDue(scenario, "Nobody's", null, LocalDate.now().minusDays(1));

        mockMvc.perform(get(reportPath(scenario, "/workload"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void resolvesNamesForAWholePageRatherThanLeavingIdentifiers() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        taskDue(scenario, "Late", employee, LocalDate.now().minusDays(2));

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].assigneeName").value("Test Person"))
                .andExpect(jsonPath("$.content[0].projectName").exists());
    }

    @Test
    void agreesWithTheTaskListingAboutWhatOverdueMeans() throws Exception {
        // The drift guard. "Overdue" is expressed twice in the platform, and if the
        // two ever disagree a dashboard count and the list it links to will differ
        // by a row with nothing to say which is right.
        Scenario scenario = scenario();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        taskDue(scenario, "Open and late", null, yesterday);
        taskDue(scenario, "Due today", null, LocalDate.now());
        taskDue(scenario, "Undated", null, null);
        done(scenario, taskDue(scenario, "Finished late", null, yesterday));

        String fromReport = mockMvc.perform(get(reportPath(scenario, "/tasks/overdue"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String fromListing = mockMvc.perform(get(workspacePath(scenario) + "/tasks?overdue=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json.readTree(fromReport).get("totalElements").asLong())
                .isEqualTo(json.readTree(fromListing).get("totalElements").asLong())
                .isEqualTo(1L);
    }

    private long sum(tools.jackson.databind.JsonNode columns) {
        long total = 0;
        for (var column : columns) {
            total += column.get("count").asLong();
        }
        return total;
    }

    private String progressOf(Scenario scenario) throws Exception {
        return mockMvc.perform(get(reportPath(scenario, "/projects"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private TaskResponse raise(Scenario scenario, String title, String priority, LocalDate dueDate) {
        return taskFixtures.task(
                scenario.workspaceId(),
                scenario.projectId(),
                new CreateTaskRequest(title, null, null, null, priority, null, dueDate, null, null, null),
                scenario.adminId());
    }

    private TaskResponse raiseFor(
            Scenario scenario, String title, UUID assignee, LocalDate dueDate, Integer estimated, Integer actual) {
        return taskFixtures.task(
                scenario.workspaceId(),
                scenario.projectId(),
                new CreateTaskRequest(title, null, assignee, null, null, null, dueDate, estimated, actual, null),
                scenario.adminId());
    }
}
