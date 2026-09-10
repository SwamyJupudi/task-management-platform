package com.company.taskmanagementplatform.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * The state machine over HTTP, including what a rejected move actually answers.
 *
 * <p>{@code TaskStatusTest} proves the matrix; this proves the endpoint honours it, that a refusal is
 * a conflict rather than a validation error, and that the completion timestamp is written and cleared
 * along with the status rather than beside it.
 */
class TaskStatusTransitionIT extends TaskApiTestBase {

    @Test
    void aTaskWalksTheWholeChain() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Walk", scenario.adminId());

        move(scenario, task.id(), "IN_PROGRESS").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        move(scenario, task.id(), "REVIEW").andExpect(status().isOk());
        move(scenario, task.id(), "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void aTaskMayBeFinishedStraightFromTheBacklog() throws Exception {
        // Allowed on purpose, unlike a project moving from planning to completed.
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Chore", scenario.adminId());

        move(scenario, task.id(), "DONE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void reviewCannotBeReachedFromTheBacklog() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Unstarted", scenario.adminId());

        move(scenario, task.id(), "REVIEW").andExpect(status().isConflict());
    }

    @Test
    void aRejectedReviewGoesBackToBeingWorkedOn() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Rejected", scenario.adminId());

        move(scenario, task.id(), "IN_PROGRESS").andExpect(status().isOk());
        move(scenario, task.id(), "REVIEW").andExpect(status().isOk());
        move(scenario, task.id(), "IN_PROGRESS").andExpect(status().isOk());
        move(scenario, task.id(), "TODO").andExpect(status().isOk());
    }

    @Test
    void finishingAndReopeningClearsTheCompletionTimestamp() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Reopened", scenario.adminId());

        move(scenario, task.id(), "DONE").andExpect(jsonPath("$.completedAt").isNotEmpty());
        move(scenario, task.id(), "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void movingToTheStatusItAlreadyHoldsIsRefusedRatherThanIgnored() throws Exception {
        // A silent no-op would be indistinguishable from a real transition in the
        // activity log that phase six adds.
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Still", scenario.adminId());

        move(scenario, task.id(), "TODO").andExpect(status().isConflict());
    }

    @Test
    void aStatusThatIsNotOneOfTheFourIsARequestError() throws Exception {
        // Not a conflict: the request itself is wrong rather than the state.
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Bad", scenario.adminId());

        move(scenario, task.id(), "BLOCKED").andExpect(status().isBadRequest());
    }

    @Test
    void changingStatusNeedsItsOwnPermission() throws Exception {
        // An employee holds task:change_status, so this is about the code being
        // checked at all rather than about who holds it. The edit endpoint would
        // let the same person through on task:update.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Theirs", employee, scenario.adminId());

        move(scenario, task.id(), "IN_PROGRESS", employee).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions move(Scenario scenario, UUID taskId, String status)
            throws Exception {
        return move(scenario, taskId, status, scenario.adminId());
    }

    private org.springframework.test.web.servlet.ResultActions move(
            Scenario scenario, UUID taskId, String status, UUID actorUserId) throws Exception {
        return mockMvc.perform(post(taskPath(scenario, taskId) + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(actorUserId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status))));
    }
}
