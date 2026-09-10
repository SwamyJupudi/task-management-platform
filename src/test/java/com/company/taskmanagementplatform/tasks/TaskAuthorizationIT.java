package com.company.taskmanagementplatform.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * The write half of the two-layer rule, and the test to keep honest.
 *
 * <p>The whole point of {@code task:manage_any} is that holding {@code task:update} admits a caller
 * at the method boundary and the scope layer then decides which tasks they may actually touch. Every
 * case below holds exactly the same permission and differs only in the caller's relationship to the
 * task, so nothing here passes because of a permission difference.
 *
 * <p>The write scope is: assignee, reporter, project owner, or lead of the project's team.
 */
class TaskAuthorizationIT extends TaskApiTestBase {

    @Test
    void anEmployeeMayEditTheirOwnTask() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse mine = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Mine", employee, scenario.adminId());

        edit(scenario, mine.id(), employee).andExpect(status().isOk());
    }

    @Test
    void anEmployeeMayEditATaskTheyRaised() throws Exception {
        // The reporter is in the write scope as well as the assignee: somebody who
        // raised a ticket can correct it before anybody picks it up.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse raised = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Raised", employee);

        edit(scenario, raised.id(), employee).andExpect(status().isOk());
    }

    @Test
    void anEmployeeMayNotEditSomebodyElsesTaskOnTheSameProject() throws Exception {
        // Visible to them, so 403 rather than 404. Pretending it were missing would
        // be misleading: they can see it in the list they just fetched.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID colleague = projectMember(scenario, "EMPLOYEE");
        TaskResponse theirs = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Theirs", colleague, scenario.adminId());

        edit(scenario, theirs.id(), employee).andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeMayNotEditATaskInAProjectTheyAreNotOn() throws Exception {
        // A different answer from the case above, on purpose. They cannot see this
        // one at all, so it is reported missing rather than forbidden.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        ProjectResponse elsewhere = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        TaskResponse hidden =
                taskFixtures.task(scenario.workspaceId(), elsewhere.id(), "Hidden", scenario.adminId());

        edit(scenario, hidden.id(), employee).andExpect(status().isNotFound());
    }

    @Test
    void aTeamLeadMayEditAnyTaskInAProjectWhoseTeamTheyLead() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), "Team " + UUID.randomUUID().toString().substring(0, 8), lead, scenario.adminId())
                .id();
        ProjectResponse led =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());

        TaskResponse task = taskFixtures.task(scenario.workspaceId(), led.id(), "Team work", scenario.adminId());

        edit(scenario, task.id(), lead).andExpect(status().isOk());
    }

    @Test
    void aTeamLeadMayNotEditATaskInAProjectTheyMerelyBelongTo() throws Exception {
        // Belonging to a project puts its tasks in reach for reading and grants
        // nothing over them. Leading the team does. Same permission, same person,
        // different relationship.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        taskFixtures.addProjectMember(scenario.workspaceId(), scenario.projectId(), lead, scenario.adminId());

        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Not theirs", scenario.adminId());

        edit(scenario, task.id(), lead).andExpect(status().isForbidden());
    }

    @Test
    void aProjectOwnerMayEditEveryTaskOnTheirProject() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        ProjectResponse owned =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), lead, null, scenario.adminId());

        TaskResponse task = taskFixtures.task(scenario.workspaceId(), owned.id(), "Owned", scenario.adminId());

        edit(scenario, task.id(), lead).andExpect(status().isOk());
    }

    @Test
    void anAdministratorReachesEveryTaskThroughTheWorkspaceWideGrant() throws Exception {
        // The same permission the employee held two tests above, plus
        // task:manage_any, which is the only difference that matters.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse theirs = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Theirs", employee, scenario.adminId());

        edit(scenario, theirs.id(), scenario.adminId()).andExpect(status().isOk());
    }

    @Test
    void aTeamLeadMayNotDeleteATaskInAProjectTheyLead() throws Exception {
        // The one task operation a lead cannot perform, matching the project rule
        // one level up: removing work is the administrator's.
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), "Team " + UUID.randomUUID().toString().substring(0, 8), lead, scenario.adminId())
                .id();
        ProjectResponse led =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), led.id(), "Doomed", scenario.adminId());

        mockMvc.perform(delete(taskPath(scenario, task.id())).header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeCannotDeleteTheirOwnTaskEither() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse mine = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Mine", employee, scenario.adminId());

        mockMvc.perform(delete(taskPath(scenario, mine.id())).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorMayDelete() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Doomed", scenario.adminId());

        mockMvc.perform(delete(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNoContent());
    }

    @Test
    void somebodyOutsideTheWorkspaceSeesNothingAtAll() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Private", scenario.adminId());
        UUID stranger = fixtures.verifiedUser(uniqueEmail("stranger")).id();

        edit(scenario, task.id(), stranger).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions edit(Scenario scenario, UUID taskId, UUID actorUserId)
            throws Exception {
        return mockMvc.perform(patch(taskPath(scenario, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorUserId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("description", "Edited"))));
    }
}
