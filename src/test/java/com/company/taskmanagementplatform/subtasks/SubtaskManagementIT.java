package com.company.taskmanagementplatform.subtasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.subtasks.dto.SubtaskResponse;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.TaskFixtures;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * The checklist under a task: what the requirements ask it to track, and who may change it.
 *
 * <p>The requirements name completion, assignee, status and due date. Completion and status are one
 * fact here, so the interesting assertions are that ticking an item off records a time, that
 * un-ticking it clears the time, and that nothing can put the two out of step.
 *
 * <p>Authorization is entirely the parent task's. There is no subtask permission family, so every
 * refusal below is the task guard refusing, one level up.
 */
class SubtaskManagementIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private TaskFixtures taskFixtures;

    @Test
    void anItemStartsUnfinishedAndAtTheEndOfTheChecklist() throws Exception {
        Scenario scenario = scenario();

        create(scenario, "Create database entities")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.position").value(0));

        create(scenario, "Implement registration").andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void theChecklistComesBackInOrder() throws Exception {
        Scenario scenario = scenario();
        create(scenario, "One");
        create(scenario, "Two");
        create(scenario, "Three");

        mockMvc.perform(get(subtasksOf(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("One"))
                .andExpect(jsonPath("$[2].title").value("Three"));
    }

    @Test
    void tickingAnItemOffRecordsWhenItHappened() throws Exception {
        Scenario scenario = scenario();
        SubtaskResponse item = taskFixtures.subtask(scenario.taskRef(), "Implement JWT", scenario.adminId());

        move(scenario, item.id(), "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void unTickingItClearsTheTimeAgain() throws Exception {
        Scenario scenario = scenario();
        SubtaskResponse item = taskFixtures.subtask(scenario.taskRef(), "Implement JWT", scenario.adminId());

        move(scenario, item.id(), "DONE").andExpect(status().isOk());
        move(scenario, item.id(), "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void anItemFollowsTheSameStateMachineAsItsTask() throws Exception {
        Scenario scenario = scenario();
        SubtaskResponse item = taskFixtures.subtask(scenario.taskRef(), "Item", scenario.adminId());

        move(scenario, item.id(), "REVIEW").andExpect(status().isConflict());
        move(scenario, item.id(), "TODO").andExpect(status().isConflict());
        move(scenario, item.id(), "IN_PROGRESS").andExpect(status().isOk());
        move(scenario, item.id(), "REVIEW").andExpect(status().isOk());
    }

    @Test
    void anAssigneeMustBeOnTheParentTasksProject() throws Exception {
        Scenario scenario = scenario();
        UUID outsider = workspaceMember(scenario, "EMPLOYEE");

        mockMvc.perform(post(subtasksOf(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("title", "Assigned", "assigneeUserId", outsider.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAssigneeWhoIsOnItIsAccepted() throws Exception {
        Scenario scenario = scenario();
        UUID member = workspaceMember(scenario, "EMPLOYEE");
        taskFixtures.addProjectMember(scenario.workspaceId(), scenario.projectId(), member, scenario.adminId());

        mockMvc.perform(post(subtasksOf(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("title", "Assigned", "assigneeUserId", member.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeUserId").value(member.toString()));
    }

    @Test
    void editingLeavesOmittedFieldsAlone() throws Exception {
        Scenario scenario = scenario();
        SubtaskResponse item = taskFixtures.subtask(scenario.taskRef(), "Original", scenario.adminId());

        mockMvc.perform(patch(subtasksOf(scenario) + "/" + item.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("dueDate", "2026-06-01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original"))
                .andExpect(jsonPath("$.dueDate").value("2026-06-01"));
    }

    @Test
    void aRemovedItemLeavesTheChecklist() throws Exception {
        Scenario scenario = scenario();
        SubtaskResponse item = taskFixtures.subtask(scenario.taskRef(), "Doomed", scenario.adminId());

        mockMvc.perform(delete(subtasksOf(scenario) + "/" + item.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(subtasksOf(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anItemOfAnotherTaskIsReportedMissing() throws Exception {
        // The repository takes the parent as well as the identifier, so a subtask
        // from a different task is indistinguishable from one that never existed.
        Scenario scenario = scenario();
        TaskResponse other =
                taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Other", scenario.adminId());
        SubtaskResponse elsewhere = taskFixtures.subtask(taskFixtures.ref(other), "Theirs", scenario.adminId());

        mockMvc.perform(patch(subtasksOf(scenario) + "/" + elsewhere.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Renamed"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTaskNobodyCanSeeHasNoReachableChecklist() throws Exception {
        Scenario scenario = scenario();
        UUID outsider = workspaceMember(scenario, "EMPLOYEE");
        taskFixtures.subtask(scenario.taskRef(), "Private", scenario.adminId());

        mockMvc.perform(get(subtasksOf(scenario)).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmployeeMayRunTheChecklistOfTheirOwnTask() throws Exception {
        Scenario scenario = scenario();
        UUID employee = workspaceMember(scenario, "EMPLOYEE");
        taskFixtures.addProjectMember(scenario.workspaceId(), scenario.projectId(), employee, scenario.adminId());

        TaskResponse mine = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Mine", employee, scenario.adminId());

        mockMvc.perform(post(subtasksOfTask(scenario, mine.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "My step"))))
                .andExpect(status().isCreated());
    }

    @Test
    void anEmployeeMayNotRunSomebodyElsesChecklist() throws Exception {
        Scenario scenario = scenario();
        UUID employee = workspaceMember(scenario, "EMPLOYEE");
        taskFixtures.addProjectMember(scenario.workspaceId(), scenario.projectId(), employee, scenario.adminId());

        mockMvc.perform(post(subtasksOf(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Not mine"))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ----------------------------------------------------------

    private Scenario scenario() {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));
        WorkspaceResponse workspace = fixtures.workspace("Subtasks", uniqueSlug("subtasks"), platform.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");

        ProjectResponse project = taskFixtures.project(workspace.id(), uniqueKey(), admin.id());
        TaskResponse task =
                taskFixtures.task(workspace.id(), project.id(), "Build Authentication", admin.id());

        return new Scenario(workspace.id(), admin.id(), project.id(), task.id());
    }

    private UUID workspaceMember(Scenario scenario, String roleSlug) {
        UserAccount person = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(Locale.ROOT)));
        fixtures.addMember(scenario.workspaceId(), person.id(), roleSlug);
        return person.id();
    }

    private String bearer(UUID userId) {
        return fixtures.bearer(userId);
    }

    private org.springframework.test.web.servlet.ResultActions create(Scenario scenario, String title)
            throws Exception {
        return mockMvc.perform(post(subtasksOf(scenario))
                .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", title))));
    }

    private org.springframework.test.web.servlet.ResultActions move(Scenario scenario, UUID subtaskId, String status)
            throws Exception {
        return mockMvc.perform(post(subtasksOf(scenario) + "/" + subtaskId + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status))));
    }

    private static String subtasksOf(Scenario scenario) {
        return subtasksOfTask(scenario, scenario.taskId());
    }

    private static String subtasksOfTask(Scenario scenario, UUID taskId) {
        return "/api/v1/workspaces/" + scenario.workspaceId() + "/tasks/" + taskId + "/subtasks";
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private record Scenario(UUID workspaceId, UUID adminId, UUID projectId, UUID taskId) {

        com.company.taskmanagementplatform.tasks.TaskRef taskRef() {
            return new com.company.taskmanagementplatform.tasks.TaskRef(taskId, projectId, workspaceId);
        }
    }
}
