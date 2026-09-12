package com.company.taskmanagementplatform.reports;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.TaskFixtures;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * The scaffolding the dashboard and report tests share.
 *
 * <p>Not itself a test, and named so that neither Surefire nor Failsafe collects it. Nine tests in
 * this package need the same workspace, administrator and project, and repeating those four lines in
 * each of them makes the difference between the tests harder to see rather than easier.
 *
 * <p>Everything is built through the ordinary services, as {@link TaskFixtures} explains. The one
 * exception in this package is {@code ReportTrendsIT}, which writes {@code completed_at} in SQL
 * because the task service sets it from the wall clock and no test can otherwise produce a task
 * finished last Tuesday.
 */
abstract class ReportApiTestBase extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected IdentityFixtures fixtures;

    @Autowired
    protected TaskFixtures taskFixtures;

    /** A workspace with an administrator and one project, all through the ordinary services. */
    protected Scenario scenario() {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));
        WorkspaceResponse workspace = fixtures.workspace("Reports", uniqueSlug("reports"), platform.id());

        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");

        String key = uniqueKey();
        ProjectResponse project = taskFixtures.project(workspace.id(), key, admin.id());

        return new Scenario(workspace.id(), admin.id(), project.id(), key);
    }

    /** Somebody in the workspace holding the named role, and not on any project. */
    protected UUID member(Scenario scenario, String roleSlug) {
        UserAccount person = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(Locale.ROOT)));
        fixtures.addMember(scenario.workspaceId(), person.id(), roleSlug);
        return person.id();
    }

    /** The same, put on the scenario's project so their work is inside their own reach. */
    protected UUID projectMember(Scenario scenario, String roleSlug) {
        UUID userId = member(scenario, roleSlug);
        taskFixtures.addProjectMember(scenario.workspaceId(), scenario.projectId(), userId, scenario.adminId());
        return userId;
    }

    /** A task with a due date, which is what most of the figures in this phase turn on. */
    protected TaskResponse taskDue(Scenario scenario, String title, UUID assignee, LocalDate dueDate) {
        return taskDue(scenario, scenario.projectId(), title, assignee, dueDate);
    }

    protected TaskResponse taskDue(
            Scenario scenario, UUID projectId, String title, UUID assignee, LocalDate dueDate) {
        return taskFixtures.task(
                scenario.workspaceId(),
                projectId,
                new CreateTaskRequest(title, null, assignee, null, null, null, dueDate, null, null, null),
                scenario.adminId());
    }

    protected TaskResponse done(Scenario scenario, TaskResponse task) {
        return taskFixtures.moveTask(scenario.workspaceId(), task.id(), "DONE", scenario.adminId());
    }

    protected String bearer(UUID userId) {
        return fixtures.bearer(userId);
    }

    protected static String workspacePath(Scenario scenario) {
        return "/api/v1/workspaces/" + scenario.workspaceId();
    }

    protected static String reportPath(Scenario scenario, String report) {
        return workspacePath(scenario) + "/reports" + report;
    }

    protected static String myDashboard(Scenario scenario) {
        return workspacePath(scenario) + "/dashboard/me";
    }

    protected static String workspaceDashboard(Scenario scenario) {
        return workspacePath(scenario) + "/dashboard/workspace";
    }

    protected static String teamDashboard(Scenario scenario, UUID teamId) {
        return workspacePath(scenario) + "/teams/" + teamId + "/dashboard";
    }

    protected static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    protected static String uniqueTeamName() {
        return "Team " + UUID.randomUUID().toString().substring(0, 8);
    }

    protected record Scenario(UUID workspaceId, UUID adminId, UUID projectId, String projectKey) {}
}
