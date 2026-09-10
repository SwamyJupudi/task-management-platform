package com.company.taskmanagementplatform.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.projects.ProjectMembershipService;
import com.company.taskmanagementplatform.projects.ProjectService;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * The cleanup that lets somebody leave at all.
 *
 * <p>{@code TaskSchemaIT} proves the database refuses to remove a project member who still holds a
 * task. This proves the listeners undo that state first, in the right order, so the removal succeeds
 * rather than failing with a constraint violation nobody could act on.
 *
 * <p>The ordering is the fragile part. The tasks and subtasks listeners run at the highest
 * precedence and {@code ProjectCleanupListener} a hundred behind them, because that one deletes the
 * membership rows the task foreign key points at. Two listeners sharing a precedence would run in an
 * order Spring does not define, and the failure would appear only when a member happened to have work
 * assigned.
 */
class TaskCascadeIT extends TaskApiTestBase {

    @Autowired
    private MembershipService workspaceMembers;

    @Autowired
    private ProjectMembershipService projectMembers;

    @Autowired
    private ProjectService projects;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void removingAProjectMemberUnassignsTheirTasksFirst() {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Theirs", employee, scenario.adminId());

        assertThatCode(() -> projectMembers.removeMember(
                        scenario.workspaceId(), scenario.projectId(), employee, scenario.adminId()))
                .doesNotThrowAnyException();

        assertThat(assigneeOf(task.id())).isNull();
    }

    @Test
    void removingAWorkspaceMemberUnassignsTheirTasksEverywhereInIt() {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Theirs", employee, scenario.adminId());

        assertThatCode(() -> workspaceMembers.removeMember(scenario.workspaceId(), employee))
                .doesNotThrowAnyException();

        assertThat(assigneeOf(task.id())).isNull();
    }

    @Test
    void removingAWorkspaceMemberAlsoStandsThemDownAsReporter() {
        // The reporter is a foreign key into workspace_members, so this one blocks
        // the removal just as the assignee does, and it has to be cleared even on
        // tasks that were already deleted.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        TaskResponse live = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Live", employee);
        TaskResponse removed = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Removed", employee);
        taskFixtures.deleteTask(scenario.workspaceId(), removed.id(), scenario.adminId());

        assertThatCode(() -> workspaceMembers.removeMember(scenario.workspaceId(), employee))
                .doesNotThrowAnyException();

        assertThat(reporterOf(live.id())).isNull();
        assertThat(reporterOf(removed.id())).isNull();
    }

    @Test
    void aTaskAssignedInOneProjectSurvivesTheirRemovalFromAnother() {
        // The project-scoped listener must narrow to the project it was told about
        // rather than sweeping the workspace.
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");

        var other = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskFixtures.addProjectMember(scenario.workspaceId(), other.id(), employee, scenario.adminId());

        TaskResponse here = taskFixtures.task(
                scenario.workspaceId(), scenario.projectId(), "Here", employee, scenario.adminId());
        TaskResponse there =
                taskFixtures.task(scenario.workspaceId(), other.id(), "There", employee, scenario.adminId());

        projectMembers.removeMember(scenario.workspaceId(), scenario.projectId(), employee, scenario.adminId());

        assertThat(assigneeOf(here.id())).isNull();
        assertThat(assigneeOf(there.id())).isEqualTo(employee);
    }

    @Test
    void removingAProjectRemovesItsTasksAndTheirSubtasks() {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Doomed", scenario.adminId());
        taskFixtures.subtask(taskFixtures.ref(task), "Doomed too", scenario.adminId());

        projects.delete(scenario.workspaceId(), scenario.projectId(), scenario.adminId());

        assertThat(liveTasksIn(scenario.projectId())).isZero();
        assertThat(liveSubtasksIn(scenario.projectId())).isZero();
    }

    @Test
    void removingATaskRemovesItsSubtasks() {
        Scenario scenario = scenario();
        TaskResponse task = taskFixtures.task(scenario.workspaceId(), scenario.projectId(), "Doomed", scenario.adminId());
        taskFixtures.subtask(taskFixtures.ref(task), "Doomed too", scenario.adminId());

        taskFixtures.deleteTask(scenario.workspaceId(), task.id(), scenario.adminId());

        assertThat(liveSubtasksOf(task.id())).isZero();
    }

    private UUID assigneeOf(UUID taskId) {
        return jdbc.queryForObject("SELECT assignee_user_id FROM tasks WHERE id = ?", UUID.class, taskId);
    }

    private UUID reporterOf(UUID taskId) {
        return jdbc.queryForObject("SELECT reporter_user_id FROM tasks WHERE id = ?", UUID.class, taskId);
    }

    private Integer liveTasksIn(UUID projectId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE project_id = ? AND deleted_at IS NULL", Integer.class, projectId);
    }

    private Integer liveSubtasksIn(UUID projectId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM subtasks WHERE project_id = ? AND deleted_at IS NULL",
                Integer.class,
                projectId);
    }

    private Integer liveSubtasksOf(UUID taskId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM subtasks WHERE task_id = ? AND deleted_at IS NULL", Integer.class, taskId);
    }
}
