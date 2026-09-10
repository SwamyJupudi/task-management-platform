package com.company.taskmanagementplatform.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * The constraints the tasks schema relies on, exercised against a real PostgreSQL.
 *
 * <p>Written at the SQL level on purpose, like {@code IdentitySchemaIT}, {@code TeamSchemaIT} and
 * {@code ProjectSchemaIT}. These rules exist so that a mistake in the service layer cannot corrupt
 * the data, so driving them through the service layer would prove the wrong thing entirely.
 */
class TaskSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdentityFixtures fixtures;

    // --- the two rules that pin a task to its people -----------------------

    @Test
    void refusesAnAssigneeWhoIsNotAMemberOfTheProject() {
        // The rule that makes task visibility and project visibility the same
        // thing: you cannot be given work in a project you are not on.
        Fixture fixture = workspaceWithProject();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));
        fixtures.addMember(fixture.workspaceId(), outsider.id(), "EMPLOYEE");

        assertThatThrownBy(() -> insertTask(fixture, 1, outsider.id(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsAnAssigneeWhoIs() {
        Fixture fixture = workspaceWithProject();
        insertProjectMember(fixture.projectId(), fixture.workspaceId(), fixture.userId());

        assertThatCode(() -> insertTask(fixture, 1, fixture.userId(), null)).doesNotThrowAnyException();
    }

    @Test
    void refusesAReporterWhoIsNotAMemberOfTheWorkspace() {
        Fixture fixture = workspaceWithProject();
        UserAccount stranger = fixtures.verifiedUser(uniqueEmail("stranger"));

        assertThatThrownBy(() -> insertTask(fixture, 1, null, stranger.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsAReporterWhoIsInTheWorkspaceButNotOnTheProject() {
        // Deliberately a different rule from the assignee's. An administrator may
        // raise a task on a project they are not a member of.
        Fixture fixture = workspaceWithProject();

        assertThatCode(() -> insertTask(fixture, 1, null, fixture.userId())).doesNotThrowAnyException();
    }

    @Test
    void refusesRemovingAProjectMemberWhoStillHoldsATask() {
        // The consequence of the assignee key, and the reason the tasks module has
        // to stand somebody down before the projects module deletes their row.
        Fixture fixture = workspaceWithProject();
        insertProjectMember(fixture.projectId(), fixture.workspaceId(), fixture.userId());
        insertTask(fixture, 1, fixture.userId(), null);

        assertThatThrownBy(() -> jdbc.update(
                        "DELETE FROM project_members WHERE project_id = ? AND user_id = ?",
                        fixture.projectId(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesATaskWhoseProjectIsInAnotherWorkspace() {
        Fixture mine = workspaceWithProject();
        Fixture theirs = workspaceWithProject();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO tasks (workspace_id, project_id, task_number, title, created_by_user_id)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        mine.workspaceId(),
                        theirs.projectId(),
                        1,
                        "Cross workspace",
                        mine.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- numbering ---------------------------------------------------------

    @Test
    void refusesTwoTasksWithTheSameNumberInOneProject() {
        Fixture fixture = workspaceWithProject();
        insertTask(fixture, 7, null, null);

        assertThatThrownBy(() -> insertTask(fixture, 7, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aDeletedTaskStillReservesItsNumber() {
        // Deliberately not a partial unique, unlike every other unique in this
        // schema. A link to PROJ-12 must not later resolve to a different task.
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 7, null, null);
        jdbc.update("UPDATE tasks SET deleted_at = now() WHERE id = ?", taskId);

        assertThatThrownBy(() -> insertTask(fixture, 7, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameNumberInAnotherProjectIsFine() {
        Fixture mine = workspaceWithProject();
        Fixture theirs = workspaceWithProject();
        insertTask(mine, 1, null, null);

        assertThatCode(() -> insertTask(theirs, 1, null, null)).doesNotThrowAnyException();
    }

    @Test
    void refusesANumberBelowOne() {
        Fixture fixture = workspaceWithProject();

        assertThatThrownBy(() -> insertTask(fixture, 0, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theCounterIsAtomicUnderAnUpsert() {
        // The statement the allocator runs, twice, proving it increments rather
        // than conflicting.
        Fixture fixture = workspaceWithProject();

        assertThat(allocate(fixture)).isEqualTo(1);
        assertThat(allocate(fixture)).isEqualTo(2);
        assertThat(allocate(fixture)).isEqualTo(3);
    }

    // --- value rules -------------------------------------------------------

    @Test
    void refusesADueDateBeforeTheStartDate() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE tasks SET start_date = DATE '2026-03-01', due_date = DATE '2026-02-01' WHERE id = ?",
                        taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesNegativeEffort() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);

        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET estimated_minutes = -1 WHERE id = ?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET actual_minutes = -1 WHERE id = ?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAStatusOutsideTheFour() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);

        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET status = 'BLOCKED' WHERE id = ?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesABlankTitle() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);

        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET title = '   ' WHERE id = ?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theCompletionTimestampCannotDisagreeWithTheStatus() {
        // Both directions. A done task without a timestamp and an unfinished task
        // with one are equally wrong, and neither is expressible.
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);

        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET status = 'DONE' WHERE id = ?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET completed_at = now() WHERE id = ?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbc.update(
                        "UPDATE tasks SET status = 'DONE', completed_at = now() WHERE id = ?", taskId))
                .doesNotThrowAnyException();
    }

    // --- subtasks ----------------------------------------------------------

    @Test
    void refusesASubtaskAssigneeWhoIsNotOnTheProject() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);

        assertThatThrownBy(() -> insertSubtask(fixture, taskId, fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesASubtaskRecordedAgainstTheWrongProject() {
        Fixture mine = workspaceWithProject();
        Fixture theirs = workspaceWithProject();
        UUID taskId = insertTask(mine, 1, null, null);

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO subtasks (workspace_id, project_id, task_id, title, created_by_user_id)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        mine.workspaceId(),
                        theirs.projectId(),
                        taskId,
                        "Wrong project",
                        mine.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSubtaskCompletionTimestampCannotDisagreeEither() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);
        UUID subtaskId = insertSubtask(fixture, taskId, null);

        assertThatThrownBy(() -> jdbc.update("UPDATE subtasks SET status = 'DONE' WHERE id = ?", subtaskId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- dependencies ------------------------------------------------------

    @Test
    void refusesATaskThatWaitsOnItself() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);

        assertThatThrownBy(() -> insertDependency(fixture, taskId, taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTheSameDependencyTwice() {
        Fixture fixture = workspaceWithProject();
        UUID first = insertTask(fixture, 1, null, null);
        UUID second = insertTask(fixture, 2, null, null);
        insertDependency(fixture, first, second);

        assertThatThrownBy(() -> insertDependency(fixture, first, second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheOppositeDirectionAsASeparateRow() {
        // The unique is on the ordered pair, and a two-task cycle is refused by the
        // service rather than by the table. This proves the table is not doing it.
        Fixture fixture = workspaceWithProject();
        UUID first = insertTask(fixture, 1, null, null);
        UUID second = insertTask(fixture, 2, null, null);
        insertDependency(fixture, first, second);

        assertThatCode(() -> insertDependency(fixture, second, first)).doesNotThrowAnyException();
    }

    @Test
    void refusesADependencyOnATaskInAnotherProject() {
        // The rule that makes a cross-workspace dependency unrepresentable as well,
        // since the project pins the workspace.
        Fixture mine = workspaceWithProject();
        Fixture theirs = workspaceWithProject();
        UUID here = insertTask(mine, 1, null, null);
        UUID there = insertTask(theirs, 1, null, null);

        assertThatThrownBy(() -> insertDependency(mine, here, there))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- labels ------------------------------------------------------------

    @Test
    void refusesLabellingATaskWithAnotherWorkspacesLabel() {
        Fixture mine = workspaceWithProject();
        Fixture theirs = workspaceWithProject();
        UUID taskId = insertTask(mine, 1, null, null);
        UUID foreignLabel = insertLabel(theirs.workspaceId(), uniqueLabel());

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO task_labels (task_id, label_id, workspace_id) VALUES (?, ?, ?)",
                        taskId,
                        foreignLabel,
                        mine.workspaceId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsLabellingWithItsOwnWorkspacesLabel() {
        Fixture fixture = workspaceWithProject();
        UUID taskId = insertTask(fixture, 1, null, null);
        UUID labelId = insertLabel(fixture.workspaceId(), uniqueLabel());

        assertThatCode(() -> jdbc.update(
                        "INSERT INTO task_labels (task_id, label_id, workspace_id) VALUES (?, ?, ?)",
                        taskId,
                        labelId,
                        fixture.workspaceId()))
                .doesNotThrowAnyException();
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspaceWithProject() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Tasks", uniqueSlug("tasks"), owner.id());
        UserAccount member = fixtures.verifiedUser(uniqueEmail("member"));
        fixtures.addMember(workspace.id(), member.id(), "EMPLOYEE");

        UUID projectId = jdbc.queryForObject(
                """
                INSERT INTO projects (workspace_id, key, name, created_by_user_id)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                UUID.class,
                workspace.id(),
                uniqueKey(),
                "Project " + UUID.randomUUID().toString().substring(0, 8),
                member.id());

        return new Fixture(workspace.id(), projectId, member.id());
    }

    private UUID insertTask(Fixture fixture, int number, UUID assigneeUserId, UUID reporterUserId) {
        return jdbc.queryForObject(
                """
                INSERT INTO tasks (workspace_id, project_id, task_number, title,
                                   assignee_user_id, reporter_user_id, created_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                UUID.class,
                fixture.workspaceId(),
                fixture.projectId(),
                number,
                "Task " + number,
                assigneeUserId,
                reporterUserId,
                fixture.userId());
    }

    private UUID insertSubtask(Fixture fixture, UUID taskId, UUID assigneeUserId) {
        return jdbc.queryForObject(
                """
                INSERT INTO subtasks (workspace_id, project_id, task_id, title,
                                      assignee_user_id, created_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                UUID.class,
                fixture.workspaceId(),
                fixture.projectId(),
                taskId,
                "Subtask",
                assigneeUserId,
                fixture.userId());
    }

    private void insertDependency(Fixture fixture, UUID taskId, UUID dependsOnTaskId) {
        jdbc.update(
                """
                INSERT INTO task_dependencies (workspace_id, project_id, task_id,
                                               depends_on_task_id, created_by_user_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                fixture.workspaceId(),
                fixture.projectId(),
                taskId,
                dependsOnTaskId,
                fixture.userId());
    }

    private void insertProjectMember(UUID projectId, UUID workspaceId, UUID userId) {
        jdbc.update(
                "INSERT INTO project_members (project_id, workspace_id, user_id) VALUES (?, ?, ?)",
                projectId,
                workspaceId,
                userId);
    }

    private UUID insertLabel(UUID workspaceId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO labels (workspace_id, name) VALUES (?, ?) RETURNING id", UUID.class, workspaceId, name);
    }

    private Integer allocate(Fixture fixture) {
        return jdbc.queryForObject(
                """
                INSERT INTO project_task_counters (project_id, workspace_id, next_number)
                VALUES (?, ?, 1)
                ON CONFLICT (project_id)
                DO UPDATE SET next_number = project_task_counters.next_number + 1
                RETURNING next_number
                """,
                Integer.class,
                fixture.projectId(),
                fixture.workspaceId());
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** Bounded at 40 characters by {@code labels_name_length_check}, so not a whole UUID. */
    private static String uniqueLabel() {
        return "label-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(UUID workspaceId, UUID projectId, UUID userId) {}
}
