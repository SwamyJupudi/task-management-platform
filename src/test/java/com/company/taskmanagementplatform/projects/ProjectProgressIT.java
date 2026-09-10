package com.company.taskmanagementplatform.projects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.TaskFixtures;
import com.company.taskmanagementplatform.tasks.TaskRef;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * The rule phase four deliberately left unimplemented, now that there are tasks to derive it from.
 *
 * <p>Progress is the share of a project's live tasks that are done, as a whole percentage rounded
 * down, with a task's unfinished checklist counting fractionally. It is written by one statement
 * inside the transaction that changed the work, so there is no read-modify-write for two people
 * finishing tasks at once to interleave inside.
 *
 * <p>The formula lives in SQL rather than in Java, so this is where it is tested. A unit test would
 * have to reimplement it, and a test of a reimplementation proves only that two versions agree.
 */
class ProjectProgressIT extends AbstractIntegrationTest {

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private TaskFixtures taskFixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aProjectWithNoTasksReadsZeroRatherThanUnknown() {
        Scenario scenario = scenario();

        assertThat(progress(scenario)).isZero();
    }

    @Test
    void progressIsTheShareOfTasksThatAreDone() {
        Scenario scenario = scenario();
        TaskResponse first = task(scenario, "One");
        task(scenario, "Two");
        task(scenario, "Three");
        task(scenario, "Four");

        taskFixtures.moveTask(scenario.workspaceId(), first.id(), "DONE", scenario.adminId());

        assertThat(progress(scenario)).isEqualTo(25);
    }

    @Test
    void everyTaskDoneReadsExactlyOneHundred() {
        // The case floating point arithmetic gets wrong, and the reason the
        // statement works in numeric and clamps.
        Scenario scenario = scenario();
        for (int i = 0; i < 3; i++) {
            TaskResponse task = task(scenario, "Task " + i);
            taskFixtures.moveTask(scenario.workspaceId(), task.id(), "DONE", scenario.adminId());
        }

        assertThat(progress(scenario)).isEqualTo(100);
    }

    @Test
    void progressRoundsDownRatherThanUp() {
        // One of three is 33.3 recurring, and reporting 34 would say more work is
        // finished than actually is.
        Scenario scenario = scenario();
        TaskResponse first = task(scenario, "One");
        task(scenario, "Two");
        task(scenario, "Three");

        taskFixtures.moveTask(scenario.workspaceId(), first.id(), "DONE", scenario.adminId());

        assertThat(progress(scenario)).isEqualTo(33);
    }

    @Test
    void unfinishedStatusesContributeNothingOnTheirOwn() {
        // A percentage that moved when nothing finished would be a guess presented
        // as a measurement. Partial credit comes from subtasks and nowhere else.
        Scenario scenario = scenario();
        TaskResponse doing = task(scenario, "Doing");
        TaskResponse reviewing = task(scenario, "Reviewing");

        taskFixtures.moveTask(scenario.workspaceId(), doing.id(), "IN_PROGRESS", scenario.adminId());
        taskFixtures.moveTask(scenario.workspaceId(), reviewing.id(), "IN_PROGRESS", scenario.adminId());
        taskFixtures.moveTask(scenario.workspaceId(), reviewing.id(), "REVIEW", scenario.adminId());

        assertThat(progress(scenario)).isZero();
    }

    @Test
    void aTaskWithSubtasksContributesTheShareOfThemThatAreDone() {
        Scenario scenario = scenario();
        TaskResponse task = task(scenario, "With a checklist");
        TaskRef ref = taskFixtures.ref(task);

        var first = taskFixtures.subtask(ref, "One", scenario.adminId());
        taskFixtures.subtask(ref, "Two", scenario.adminId());
        taskFixtures.subtask(ref, "Three", scenario.adminId());
        taskFixtures.subtask(ref, "Four", scenario.adminId());

        assertThat(progress(scenario)).isZero();

        taskFixtures.moveSubtask(ref, first.id(), "DONE", scenario.adminId());

        // One task, a quarter of its checklist done.
        assertThat(progress(scenario)).isEqualTo(25);
    }

    @Test
    void aDoneTaskCountsFullyEvenWithAnUnfinishedChecklist() {
        // The clarification to the rule recorded in database.md. A task marked done
        // is done; averaging it with its leftovers would report less than the truth.
        Scenario scenario = scenario();
        TaskResponse task = task(scenario, "Done anyway");
        TaskRef ref = taskFixtures.ref(task);

        taskFixtures.subtask(ref, "One", scenario.adminId());
        taskFixtures.subtask(ref, "Two", scenario.adminId());
        taskFixtures.subtask(ref, "Three", scenario.adminId());

        taskFixtures.moveTask(scenario.workspaceId(), task.id(), "DONE", scenario.adminId());

        assertThat(progress(scenario)).isEqualTo(100);
    }

    @Test
    void aDeletedTaskLeavesBothSidesOfTheFraction() {
        Scenario scenario = scenario();
        TaskResponse kept = task(scenario, "Kept");
        TaskResponse removed = task(scenario, "Removed");

        taskFixtures.moveTask(scenario.workspaceId(), kept.id(), "DONE", scenario.adminId());
        assertThat(progress(scenario)).isEqualTo(50);

        taskFixtures.deleteTask(scenario.workspaceId(), removed.id(), scenario.adminId());
        assertThat(progress(scenario)).isEqualTo(100);
    }

    @Test
    void aProjectWhoseOnlyTaskIsDeletedGoesBackToZero() {
        Scenario scenario = scenario();
        TaskResponse only = task(scenario, "Only");
        taskFixtures.moveTask(scenario.workspaceId(), only.id(), "DONE", scenario.adminId());
        assertThat(progress(scenario)).isEqualTo(100);

        taskFixtures.deleteTask(scenario.workspaceId(), only.id(), scenario.adminId());

        assertThat(progress(scenario)).isZero();
    }

    @Test
    void aDeletedSubtaskLeavesItsTasksFractionToo() {
        Scenario scenario = scenario();
        TaskResponse task = task(scenario, "With a checklist");
        TaskRef ref = taskFixtures.ref(task);

        var done = taskFixtures.subtask(ref, "Done", scenario.adminId());
        var pending = taskFixtures.subtask(ref, "Pending", scenario.adminId());
        taskFixtures.moveSubtask(ref, done.id(), "DONE", scenario.adminId());

        assertThat(progress(scenario)).isEqualTo(50);

        taskFixtures.deleteSubtask(ref, pending.id(), scenario.adminId());

        assertThat(progress(scenario)).isEqualTo(100);
    }

    @Test
    void aTaskWhoseSubtasksAreAllDeletedFallsBackToItsOwnStatus() {
        Scenario scenario = scenario();
        TaskResponse task = task(scenario, "Emptied");
        TaskRef ref = taskFixtures.ref(task);

        var only = taskFixtures.subtask(ref, "Only", scenario.adminId());
        taskFixtures.moveSubtask(ref, only.id(), "DONE", scenario.adminId());
        assertThat(progress(scenario)).isEqualTo(100);

        taskFixtures.deleteSubtask(ref, only.id(), scenario.adminId());

        // No checklist left, and the task itself is not done.
        assertThat(progress(scenario)).isZero();
    }

    @Test
    void reopeningAFinishedTaskTakesTheProgressBackDown() {
        Scenario scenario = scenario();
        TaskResponse first = task(scenario, "One");
        task(scenario, "Two");
        taskFixtures.moveTask(scenario.workspaceId(), first.id(), "DONE", scenario.adminId());
        assertThat(progress(scenario)).isEqualTo(50);

        taskFixtures.moveTask(scenario.workspaceId(), first.id(), "IN_PROGRESS", scenario.adminId());

        assertThat(progress(scenario)).isZero();
    }

    @Test
    void oneProjectsWorkNeverMovesAnother() {
        Scenario scenario = scenario();
        ProjectResponse other = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());

        TaskResponse mine = task(scenario, "Mine");
        taskFixtures.task(scenario.workspaceId(), other.id(), "Theirs", scenario.adminId());
        taskFixtures.moveTask(scenario.workspaceId(), mine.id(), "DONE", scenario.adminId());

        assertThat(progress(scenario)).isEqualTo(100);
        assertThat(progressOf(other.id())).isZero();
    }

    // --- helpers ----------------------------------------------------------

    private Scenario scenario() {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));
        WorkspaceResponse workspace = fixtures.workspace("Progress", uniqueSlug("progress"), platform.id());
        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");

        ProjectResponse project = taskFixtures.project(workspace.id(), uniqueKey(), admin.id());
        return new Scenario(workspace.id(), admin.id(), project.id());
    }

    private TaskResponse task(Scenario scenario, String title) {
        return taskFixtures.task(scenario.workspaceId(), scenario.projectId(), title, scenario.adminId());
    }

    private int progress(Scenario scenario) {
        return progressOf(scenario.projectId());
    }

    private int progressOf(UUID projectId) {
        Integer stored = jdbc.queryForObject("SELECT progress FROM projects WHERE id = ?", Integer.class, projectId);
        return stored == null ? 0 : stored;
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private record Scenario(UUID workspaceId, UUID adminId, UUID projectId) {}
}
