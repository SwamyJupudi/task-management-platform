package com.company.taskmanagementplatform.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.TaskFixtures;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * The task lifecycle through the API: raising one, editing it, and removing it.
 *
 * <p>The numbering is the part worth watching. A task is known by its project's key and its own
 * number, that pair has to be stable for as long as the project exists, and neither half is stored in
 * the form people read.
 */
class TaskManagementIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private TaskFixtures taskFixtures;

    @Test
    void raisingATaskNumbersItFromOneAndRendersTheProjectKey() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Implement rotation"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskNumber").value(1))
                .andExpect(jsonPath("$.key").value(scenario.projectKey() + "-1"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.projectKey").value(scenario.projectKey()));
    }

    @Test
    void numbersRunConsecutivelyWithinAProjectAndRestartInAnother() throws Exception {
        Scenario scenario = scenario();
        ProjectResponse other = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());

        taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "One", scenario.adminId());
        TaskResponse second = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Two", scenario.adminId());
        TaskResponse elsewhere = taskFixtures.task(scenario.workspaceId(), other.id(), "Elsewhere", scenario.adminId());

        org.assertj.core.api.Assertions.assertThat(second.taskNumber()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(elsewhere.taskNumber()).isEqualTo(1);
    }

    @Test
    void aRemovedTaskNeverGivesItsNumberBack() throws Exception {
        // The point of the non-partial unique. A link to PROJ-1 must not later
        // resolve to something else, so the project's numbering shows a gap.
        Scenario scenario = scenario();
        TaskResponse first = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "One", scenario.adminId());

        mockMvc.perform(delete(taskPath(scenario, first.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNoContent());

        TaskResponse next = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Two", scenario.adminId());
        org.assertj.core.api.Assertions.assertThat(next.taskNumber()).isEqualTo(2);
    }

    @Test
    void theReporterDefaultsToWhoeverRaisedIt() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Raised by me"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporterUserId").value(scenario.adminId().toString()));
    }

    @Test
    void aDeletedTaskIsGoneFromReadsRatherThanFromTheTable() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Doomed", scenario.adminId());

        mockMvc.perform(delete(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingLeavesOmittedFieldsAlone() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Original", scenario.adminId());

        mockMvc.perform(patch(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("priority", "CRITICAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    void labelsAreReplacedAsASetAndFoldedOntoOneLabel() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Labelled", scenario.adminId());

        mockMvc.perform(patch(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("labels", List.of("Backend", "backend", "API")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(2));

        mockMvc.perform(patch(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("labels", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(0));
    }

    @Test
    void aTaskCannotBeDueBeforeItStarts() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("title", "Backwards", "startDate", "2026-03-01", "dueDate", "2026-02-01"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void effortIsRecordedInMinutesAndCannotBeNegative() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("title", "Estimated", "estimatedMinutes", 480, "actualMinutes", 520))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedMinutes").value(480))
                .andExpect(jsonPath("$.actualMinutes").value(520));

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Negative", "estimatedMinutes", -1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anArchivedProjectRefusesNewWorkAndStaysReadable() throws Exception {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Before", scenario.adminId());

        mockMvc.perform(post(projectPath(scenario) + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "After"))))
                .andExpect(status().isConflict());

        mockMvc.perform(patch(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Renamed"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get(taskPath(scenario, task.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());
    }

    @Test
    void anArchivedWorkspaceFreezesTasksTheSameWay() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(post(workspacePath(scenario) + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());

        mockMvc.perform(post(tasksIn(scenario))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Frozen"))))
                .andExpect(status().isConflict());
    }

    @Test
    void aTaskFromAnotherWorkspaceIsReportedMissingRatherThanForbidden() throws Exception {
        Scenario mine = scenario();
        Scenario theirs = scenario();
        TaskResponse elsewhere =
                taskFixtures.task(theirs.workspaceId(), theirs.projectId(), "Theirs", theirs.adminId());

        mockMvc.perform(get(taskPath(mine, elsewhere.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isNotFound());
    }

    // --- helpers ----------------------------------------------------------

    private Scenario scenario() {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));
        WorkspaceResponse workspace = fixtures.workspace("Tasks", uniqueSlug("tasks"), platform.id());

        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");

        String key = uniqueKey();
        ProjectResponse project = taskFixtures.project(workspace.id(), key, admin.id());

        return new Scenario(workspace.id(), admin.id(), project.id(), key);
    }

    private String bearer(UUID userId) {
        return fixtures.bearer(userId);
    }

    private static String workspacePath(Scenario scenario) {
        return "/api/v1/workspaces/" + scenario.workspaceId();
    }

    private static String projectPath(Scenario scenario) {
        return workspacePath(scenario) + "/projects/" + scenario.projectId();
    }

    private static String tasksIn(Scenario scenario) {
        return projectPath(scenario) + "/tasks";
    }

    private static String taskPath(Scenario scenario, UUID taskId) {
        return workspacePath(scenario) + "/tasks/" + taskId;
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private record Scenario(UUID workspaceId, UUID adminId, UUID projectId, String projectKey) {}
}
