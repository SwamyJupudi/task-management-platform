package com.company.taskmanagementplatform.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * The filters the requirements name, the search, and the sort allowlist over HTTP.
 *
 * <p>The requirements list assignee, project, team, status, priority, due date, labels and created
 * date as the filters a production search must have, and say the search must avoid loading
 * unnecessary records. Every one of them is here, and each is checked to narrow rather than merely to
 * be accepted.
 */
class TaskFilteringIT extends TaskApiTestBase {

    @Test
    void filtersByStatusAndAcceptsSeveralAtOnce() throws Exception {
        // Repeatable so a board fetches all four of its columns in one call.
        Scenario scenario = scenario();
        TaskResponse todo = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Todo", scenario.adminId());
        TaskResponse doing = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Doing", scenario.adminId());
        TaskResponse done = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Done", scenario.adminId());

        taskFixtures.moveTask(scenario.workspaceId(), doing.id(), "IN_PROGRESS", scenario.adminId());
        taskFixtures.moveTask(scenario.workspaceId(), done.id(), "DONE", scenario.adminId());

        search(scenario, "?status=TODO").andExpect(jsonPath("$.totalElements").value(1));
        search(scenario, "?status=TODO&status=DONE").andExpect(jsonPath("$.totalElements").value(2));

        org.assertj.core.api.Assertions.assertThat(todo.id()).isNotEqualTo(done.id());
    }

    @Test
    void filtersByPriority() throws Exception {
        Scenario scenario = scenario();
        taskFixtures.task(
                scenario.workspaceId(),
                scenario.projectId(),
                new CreateTaskRequest("Critical", null, null, null, "CRITICAL", null, null, null, null, null),
                scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Ordinary", scenario.adminId());

        search(scenario, "?priority=CRITICAL")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Critical"));
    }

    @Test
    void filtersByAssigneeAndByHavingNoneAtAll() throws Exception {
        // "Unassigned" cannot be expressed as an identifier, so it is its own flag.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Held", employee, scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Free", scenario.adminId());

        search(scenario, "?assigneeUserId=" + employee).andExpect(jsonPath("$.totalElements").value(1));
        search(scenario, "?unassigned=true")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Free"));
    }

    @Test
    void filtersByReporter() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Raised by them", employee);
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Raised by admin", scenario.adminId());

        search(scenario, "?reporterUserId=" + employee)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Raised by them"));
    }

    @Test
    void filtersByProjectAndByTeam() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), "Team " + UUID.randomUUID().toString().substring(0, 8), lead, scenario.adminId())
                .id();
        ProjectResponse teamProject =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());

        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Loose", scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), teamProject.id(), "Team work", scenario.adminId());

        search(scenario, "?projectId=" + teamProject.id()).andExpect(jsonPath("$.totalElements").value(1));
        search(scenario, "?teamId=" + team)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Team work"));
    }

    @Test
    void aTeamWithNoProjectsMatchesNothingRatherThanEverything() throws Exception {
        Scenario scenario = scenario();
        UUID team = taskFixtures
                .team(scenario.workspaceId(), "Empty " + UUID.randomUUID().toString().substring(0, 8), null, scenario.adminId())
                .id();
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Elsewhere", scenario.adminId());

        search(scenario, "?teamId=" + team).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByDueDateAndByBeingOverdue() throws Exception {
        Scenario scenario = scenario();
        LocalDate past = LocalDate.now().minusDays(7);
        LocalDate future = LocalDate.now().plusDays(7);

        taskFixtures.task(
                scenario.workspaceId(),
                scenario.projectId(),
                new CreateTaskRequest("Late", null, null, null, null, null, past, null, null, null),
                scenario.adminId());
        taskFixtures.task(
                scenario.workspaceId(),
                scenario.projectId(),
                new CreateTaskRequest("Ahead", null, null, null, null, null, future, null, null, null),
                scenario.adminId());

        search(scenario, "?dueBefore=" + LocalDate.now()).andExpect(jsonPath("$.totalElements").value(1));
        search(scenario, "?dueAfter=" + LocalDate.now()).andExpect(jsonPath("$.totalElements").value(1));
        search(scenario, "?dueOn=" + past).andExpect(jsonPath("$.totalElements").value(1));
        search(scenario, "?overdue=true")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Late"));
    }

    @Test
    void finishedWorkIsNeverOverdueHoweverLateItWas() throws Exception {
        Scenario scenario = scenario();
        TaskResponse late = taskFixtures.task(
                scenario.workspaceId(),
                scenario.projectId(),
                new CreateTaskRequest("Late but done", null, null, null, null, null, LocalDate.now().minusDays(3),
                        null, null, null),
                scenario.adminId());

        search(scenario, "?overdue=true").andExpect(jsonPath("$.totalElements").value(1));
        taskFixtures.moveTask(scenario.workspaceId(), late.id(), "DONE", scenario.adminId());
        search(scenario, "?overdue=true").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByLabel() throws Exception {
        Scenario scenario = scenario();
        String label = "backend-" + UUID.randomUUID().toString().substring(0, 8);
        taskFixtures.taskWithLabels(
                scenario.workspaceId(), scenario.projectId(), "Labelled", List.of(label), scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Plain", scenario.adminId());

        search(scenario, "?label=" + label).andExpect(jsonPath("$.totalElements").value(1));
        // Folded, so the spelling in the query does not have to match the catalog.
        search(scenario, "?label=" + label.toUpperCase(java.util.Locale.ROOT))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void aLabelNobodyHasEverUsedMatchesNothing() throws Exception {
        // Ignoring an unknown label would quietly return every task in the
        // workspace instead of none, which is the wrong way for a filter to fail.
        Scenario scenario = scenario();
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Plain", scenario.adminId());

        search(scenario, "?label=nothing-like-this").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchesTheTitleAndTheRenderedKey() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Implement rotation", scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Something else", scenario.adminId());

        search(scenario, "?q=rotation")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(task.id().toString()));

        // The form somebody would paste out of a message.
        search(scenario, "?q=" + task.key())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(task.id().toString()));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        Scenario scenario = scenario();
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Implement Rotation", scenario.adminId());

        search(scenario, "?q=ROTATION").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtersByCreatedDate() throws Exception {
        Scenario scenario = scenario();
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Now", scenario.adminId());

        search(scenario, "?createdAfter=" + java.time.Instant.now().minusSeconds(3600))
                .andExpect(jsonPath("$.totalElements").value(1));
        search(scenario, "?createdBefore=" + java.time.Instant.now().minusSeconds(3600))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pagesRatherThanReturningEverything() throws Exception {
        // The requirements are explicit that a production search must not load
        // thousands of records.
        Scenario scenario = scenario();
        for (int i = 0; i < 5; i++) {
            taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Task " + i, scenario.adminId());
        }

        search(scenario, "?size=2")
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void sortsByAnAllowedFieldAndRefusesAnythingElse() throws Exception {
        Scenario scenario = scenario();
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "First", scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Second", scenario.adminId());

        search(scenario, "?sort=taskNumber,asc")
                .andExpect(jsonPath("$.content[0].taskNumber").value(1));

        mockMvc.perform(get(workspaceTasks(scenario) + "?sort=description,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theProjectScopedListingIsTheBoardView() throws Exception {
        Scenario scenario = scenario();
        ProjectResponse other = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Here", scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), other.id(), "There", scenario.adminId());

        mockMvc.perform(get(tasksIn(scenario) + "?sort=boardPosition,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Here"));
    }

    private org.springframework.test.web.servlet.ResultActions search(Scenario scenario, String query)
            throws Exception {
        return mockMvc.perform(get(workspaceTasks(scenario) + query)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());
    }
}
