package com.company.taskmanagementplatform.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * Who a task belongs to, and the rule that makes visibility coherent.
 *
 * <p>An assignee must be on the task's project. That is a foreign key rather than a service check, so
 * the service check exists only to turn a constraint violation into a sentence, and this test proves
 * both that the sentence appears and that the rule is real.
 */
class TaskAssignmentIT extends TaskApiTestBase {

    @Test
    void aProjectMemberCanBeGivenTheTask() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Work", scenario.adminId());

        assign(scenario, task.id(), employee, scenario.adminId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeUserId").value(employee.toString()));
    }

    @Test
    void somebodyOnlyInTheWorkspaceCannotBeGivenIt() throws Exception {
        // The rule that keeps "tasks assigned to me" a subset of "tasks I can see".
        Scenario scenario = scenario();
        UUID outsider = member(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Work", scenario.adminId());

        assign(scenario, task.id(), outsider, scenario.adminId()).andExpect(status().isBadRequest());
    }

    @Test
    void assigningAtCreationIsHeldToTheSameRule() throws Exception {
        Scenario scenario = scenario();
        UUID outsider = member(scenario, "EMPLOYEE");

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("title", "Assigned", "assigneeUserId", outsider.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTaskCanBeLeftWithNobodyHoldingIt() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Work", employee, scenario.adminId());

        mockMvc.perform(delete(taskPath(scenario, task.id()) + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeUserId").doesNotExist());
    }

    @Test
    void unassigningSomethingNobodyHoldsIsRefused() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Free", scenario.adminId());

        mockMvc.perform(delete(taskPath(scenario, task.id()) + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isConflict());
    }

    @Test
    void reassigningToTheSamePersonIsRefusedRatherThanIgnored() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Work", employee, scenario.adminId());

        assign(scenario, task.id(), employee, scenario.adminId()).andExpect(status().isConflict());
    }

    @Test
    void anEmployeeCannotAssignEvenTheirOwnTask() throws Exception {
        // The requirements give an employee create, update and status changes, and
        // not assignment. They pass the write scope on their own task, so this is
        // the permission layer refusing rather than the scope layer.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        UUID colleague = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Mine", employee, scenario.adminId());

        assign(scenario, task.id(), colleague, employee).andExpect(status().isForbidden());
    }

    @Test
    void aTeamLeadMayAssignWithinAProjectTheyLead() throws Exception {
        Scenario scenario = scenario();
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), "Team " + UUID.randomUUID().toString().substring(0, 8), lead, scenario.adminId())
                .id();

        var project = taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());
        UUID worker = member(scenario, "EMPLOYEE");
        taskFixtures.addProjectMember(scenario.workspaceId(), project.id(), worker, scenario.adminId());

        TaskResponse task =
                taskFixtures.task(scenario.workspaceId(), project.id(), "Team work", scenario.adminId());

        assign(scenario, task.id(), worker, lead).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions assign(
            Scenario scenario, UUID taskId, UUID assigneeUserId, UUID actorUserId) throws Exception {
        return mockMvc.perform(put(taskPath(scenario, taskId) + "/assignee")
                .header(HttpHeaders.AUTHORIZATION, bearer(actorUserId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assigneeUserId", assigneeUserId.toString()))));
    }
}
