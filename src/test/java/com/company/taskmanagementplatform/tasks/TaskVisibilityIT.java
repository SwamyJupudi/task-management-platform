package com.company.taskmanagementplatform.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * Which tasks each person can see, and the reason this is the phase's most important test.
 *
 * <p>Read scope decides which rows a listing returns rather than whether a call is allowed, so a
 * mistake in it leaks the shape of a workspace instead of failing loudly.
 *
 * <p><strong>A task is visible exactly when its project is.</strong> There is no separate task read
 * scope, which is what keeps the two from drifting apart, so every case here is really a project
 * visibility case seen one level down: owning the project, being on it, and leading its team.
 */
class TaskVisibilityIT extends TaskApiTestBase {

    @Test
    void anEmployeeSeesOnlyTheTasksOfProjectsTheyAreOn() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Visible", scenario.adminId());

        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), elsewhere.id(), "Hidden", scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), elsewhere.id(), "Also hidden", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Visible"));
    }

    @Test
    void anEmployeeOnNoProjectSeesAnEmptyPageRatherThanAnError() throws Exception {
        // They belong to the workspace, so this is not a 404. There is simply
        // nothing of theirs in it yet.
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Not theirs", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void beingInTheSameWorkspaceGrantsNothingOnItsOwn() throws Exception {
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");
        TaskResponse hidden =
                taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Hidden", scenario.adminId());

        mockMvc.perform(get(taskPath(scenario, hidden.id())).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
    }

    @Test
    void beingInTheSameTeamGrantsNothingButLeadingItDoes() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID bystander = member(scenario, "EMPLOYEE");

        UUID team = taskFixtures
                .team(scenario.workspaceId(), "Team " + UUID.randomUUID().toString().substring(0, 8), lead, scenario.adminId())
                .id();
        ProjectResponse teamProject =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), teamProject.id(), "Team work", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(workspaceTasks(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(bystander)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void owningAProjectPutsItsTasksInReach() throws Exception {
        Scenario scenario = scenario();
        UUID owner = member(scenario, "EMPLOYEE");
        ProjectResponse owned =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), owner, null, scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), owned.id(), "Owned work", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anAdministratorSeesEveryTaskInTheWorkspace() throws Exception {
        Scenario scenario = scenario();
        ProjectResponse other = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());

        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "One", scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), other.id(), "Two", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void aFilterCannotBeUsedToWidenWhatIsVisible() throws Exception {
        // The predicate is joined to every filter with AND, inside the query, so
        // naming a project the caller cannot see returns nothing rather than
        // everything in it.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        ProjectResponse hidden = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), hidden.id(), "Secret", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario) + "?projectId=" + hidden.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listingTheTasksOfAnUnreachableProjectAnswersAsMissing() throws Exception {
        // Not an empty page, which would say the project exists and happens to be
        // empty.
        Scenario scenario = scenario();
        UUID employee = member(scenario, "EMPLOYEE");

        mockMvc.perform(get(tasksIn(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isNotFound());
    }

    @Test
    void myTasksIsASubsetOfWhatIsVisibleRatherThanAWayPastIt() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        TaskResponse mine = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Mine", employee, scenario.adminId());
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Somebody else's", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario) + "?assigneeUserId=" + employee)
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine.id().toString()));
    }

    @Test
    void tasksOfAnotherWorkspaceNeverAppear() throws Exception {
        Scenario mine = scenario();
        Scenario theirs = scenario();
        taskFixtures.task(theirs.workspaceId(), theirs.projectId(), "Theirs", theirs.adminId());
        taskFixtures.task(mine.workspaceId(), mine.projectId(), "Mine", mine.adminId());

        mockMvc.perform(get(workspaceTasks(mine)).header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Mine"));
    }

    @Test
    void aDeletedProjectTakesItsTasksOutOfEveryListing() throws Exception {
        // Including an administrator's, whose listing narrows on nothing and would
        // otherwise keep showing work with no project to click through to.
        Scenario scenario = scenario();
        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Doomed", scenario.adminId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(projectPath(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(workspaceTasks(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
