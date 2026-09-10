package com.company.taskmanagementplatform.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * One task waiting on another, and the four ways it is refused.
 *
 * <p>Three of them are constraints and one is not. Self-dependency, duplicates and crossing a project
 * are refused by the database; a cycle cannot be stated as a constraint at all and is the service's
 * job, so the transitive cases below are the ones that would actually break if somebody replaced the
 * recursive walk with a single-step check.
 */
class TaskDependencyIT extends TaskApiTestBase {

    @Test
    void oneTaskCanWaitOnAnotherInTheSameProject() throws Exception {
        Scenario scenario = scenario();
        TaskResponse blocked = task(scenario, "Blocked");
        TaskResponse blocker = task(scenario, "Blocker");

        add(scenario, blocked.id(), blocker.id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockedBy.length()").value(1))
                .andExpect(jsonPath("$.blockedBy[0].id").value(blocker.id().toString()));

        mockMvc.perform(get(dependenciesOf(scenario, blocker.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocking.length()").value(1))
                .andExpect(jsonPath("$.blocking[0].id").value(blocked.id().toString()));
    }

    @Test
    void aTaskCannotWaitOnItself() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = task(scenario, "Alone");

        add(scenario, task.id(), task.id()).andExpect(status().isBadRequest());
    }

    @Test
    void theSameDependencyCannotBeRecordedTwice() throws Exception {
        Scenario scenario = scenario();
        TaskResponse blocked = task(scenario, "Blocked");
        TaskResponse blocker = task(scenario, "Blocker");

        add(scenario, blocked.id(), blocker.id()).andExpect(status().isCreated());
        add(scenario, blocked.id(), blocker.id()).andExpect(status().isConflict());
    }

    @Test
    void aTaskInAnotherProjectIsRefused() throws Exception {
        Scenario scenario = scenario();
        ProjectResponse other = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        TaskResponse here = task(scenario, "Here");
        TaskResponse there = taskFixtures.task(scenario.workspaceId(), other.id(), "There", scenario.adminId());

        add(scenario, here.id(), there.id()).andExpect(status().isBadRequest());
    }

    @Test
    void aTaskInAnotherWorkspaceIsRefusedTheSameWay() throws Exception {
        // Deliberately the same answer as the project case. A more precise message
        // would confirm the identifier names something real.
        Scenario mine = scenario();
        Scenario theirs = scenario();
        TaskResponse here = task(mine, "Here");
        TaskResponse elsewhere =
                taskFixtures.task(theirs.workspaceId(), theirs.projectId(), "Elsewhere", theirs.adminId());

        add(mine, here.id(), elsewhere.id()).andExpect(status().isBadRequest());
    }

    @Test
    void aDirectCycleIsRefused() throws Exception {
        Scenario scenario = scenario();
        TaskResponse first = task(scenario, "First");
        TaskResponse second = task(scenario, "Second");

        add(scenario, first.id(), second.id()).andExpect(status().isCreated());
        add(scenario, second.id(), first.id()).andExpect(status().isConflict());
    }

    @Test
    void aCycleThroughAThirdTaskIsRefused() throws Exception {
        // The case a single-step check would miss, and the reason the walk is
        // recursive rather than a lookup.
        Scenario scenario = scenario();
        TaskResponse a = task(scenario, "A");
        TaskResponse b = task(scenario, "B");
        TaskResponse c = task(scenario, "C");

        add(scenario, a.id(), b.id()).andExpect(status().isCreated());
        add(scenario, b.id(), c.id()).andExpect(status().isCreated());
        add(scenario, c.id(), a.id()).andExpect(status().isConflict());
    }

    @Test
    void aLongerChainStillTerminatesAndIsStillRefused() throws Exception {
        Scenario scenario = scenario();
        TaskResponse[] chain = new TaskResponse[6];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = task(scenario, "Link " + i);
        }
        for (int i = 0; i < chain.length - 1; i++) {
            add(scenario, chain[i].id(), chain[i + 1].id()).andExpect(status().isCreated());
        }

        add(scenario, chain[chain.length - 1].id(), chain[0].id()).andExpect(status().isConflict());
    }

    @Test
    void aDiamondIsNotACycleAndIsAllowed() throws Exception {
        // Two paths to the same blocker is an ordinary shape, and a check that
        // refused it would be refusing the common case.
        Scenario scenario = scenario();
        TaskResponse top = task(scenario, "Top");
        TaskResponse left = task(scenario, "Left");
        TaskResponse right = task(scenario, "Right");
        TaskResponse bottom = task(scenario, "Bottom");

        add(scenario, top.id(), left.id()).andExpect(status().isCreated());
        add(scenario, top.id(), right.id()).andExpect(status().isCreated());
        add(scenario, left.id(), bottom.id()).andExpect(status().isCreated());
        add(scenario, right.id(), bottom.id()).andExpect(status().isCreated());
    }

    @Test
    void aDependencyCanBeRemoved() throws Exception {
        Scenario scenario = scenario();
        TaskResponse blocked = task(scenario, "Blocked");
        TaskResponse blocker = task(scenario, "Blocker");
        add(scenario, blocked.id(), blocker.id()).andExpect(status().isCreated());

        mockMvc.perform(delete(dependenciesOf(scenario, blocked.id()) + "/" + blocker.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(dependenciesOf(scenario, blocked.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(jsonPath("$.blockedBy.length()").value(0));
    }

    @Test
    void removingOneThatDoesNotExistIsReportedMissing() throws Exception {
        Scenario scenario = scenario();
        TaskResponse blocked = task(scenario, "Blocked");
        TaskResponse blocker = task(scenario, "Blocker");

        mockMvc.perform(delete(dependenciesOf(scenario, blocked.id()) + "/" + blocker.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingATaskTakesItsEdgesWithIt() throws Exception {
        // A join table is never soft deleted, and a blocker nobody can see must not
        // go on blocking something they can.
        Scenario scenario = scenario();
        TaskResponse blocked = task(scenario, "Blocked");
        TaskResponse blocker = task(scenario, "Blocker");
        add(scenario, blocked.id(), blocker.id()).andExpect(status().isCreated());

        mockMvc.perform(delete(taskPath(scenario, blocker.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(dependenciesOf(scenario, blocked.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedBy.length()").value(0));
    }

    @Test
    void aBlockedTaskIsReportedAsBlockedUntilTheBlockerIsDone() throws Exception {
        // Nothing prevents a blocked task from moving; the requirements state no
        // such rule. It is surfaced so a client can show it, and it stops counting
        // once the blocker is finished.
        Scenario scenario = scenario();
        TaskResponse blocked = task(scenario, "Blocked");
        TaskResponse blocker = task(scenario, "Blocker");
        add(scenario, blocked.id(), blocker.id()).andExpect(status().isCreated());

        mockMvc.perform(get(workspaceTasks(scenario) + "?blocked=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(blocked.id().toString()));

        taskFixtures.moveTask(scenario.workspaceId(), blocker.id(), "DONE", scenario.adminId());

        mockMvc.perform(get(workspaceTasks(scenario) + "?blocked=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anEmployeeCannotAddADependencyToSomebodyElsesTask() throws Exception {
        // No permission family of its own: this is task:update and the task write
        // scope, exactly as an edit would be.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse theirs = task(scenario, "Theirs");
        TaskResponse blocker = task(scenario, "Blocker");

        mockMvc.perform(post(dependenciesOf(scenario, theirs.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("dependsOnTaskId", blocker.id().toString()))))
                .andExpect(status().isForbidden());
    }

    private TaskResponse task(Scenario scenario, String title) {
        return taskFixtures.task(scenario.workspaceId(), scenario.projectId(), title, scenario.adminId());
    }

    private static String dependenciesOf(Scenario scenario, UUID taskId) {
        return taskPath(scenario, taskId) + "/dependencies";
    }

    private org.springframework.test.web.servlet.ResultActions add(
            Scenario scenario, UUID taskId, UUID dependsOnTaskId) throws Exception {
        return mockMvc.perform(post(dependenciesOf(scenario, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("dependsOnTaskId", dependsOnTaskId.toString()))));
    }
}
