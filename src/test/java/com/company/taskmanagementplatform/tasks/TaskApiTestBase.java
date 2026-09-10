package com.company.taskmanagementplatform.tasks;

import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.TaskFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * The scaffolding the task tests share: a workspace, an administrator, a project, and the addresses
 * of everything underneath them.
 *
 * <p>Not itself a test, and named so that neither Surefire nor Failsafe collects it. It exists
 * because six task tests need the same four lines of setup, and repeating those in each one makes the
 * difference between them harder to see rather than easier.
 */
abstract class TaskApiTestBase extends AbstractIntegrationTest {

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
        WorkspaceResponse workspace = fixtures.workspace("Tasks", uniqueSlug("tasks"), platform.id());

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

    /** The same, put on the scenario's project. */
    protected UUID projectMember(Scenario scenario, String roleSlug) {
        UUID userId = member(scenario, roleSlug);
        taskFixtures.addProjectMember(scenario.workspaceId(), scenario.projectId(), userId, scenario.adminId());
        return userId;
    }

    protected String bearer(UUID userId) {
        return fixtures.bearer(userId);
    }

    protected static String workspacePath(Scenario scenario) {
        return "/api/v1/workspaces/" + scenario.workspaceId();
    }

    protected static String projectPath(Scenario scenario) {
        return workspacePath(scenario) + "/projects/" + scenario.projectId();
    }

    protected static String tasksIn(Scenario scenario) {
        return projectPath(scenario) + "/tasks";
    }

    protected static String taskPath(Scenario scenario, UUID taskId) {
        return workspacePath(scenario) + "/tasks/" + taskId;
    }

    protected static String workspaceTasks(Scenario scenario) {
        return workspacePath(scenario) + "/tasks";
    }

    protected static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    protected record Scenario(UUID workspaceId, UUID adminId, UUID projectId, String projectKey) {}
}
