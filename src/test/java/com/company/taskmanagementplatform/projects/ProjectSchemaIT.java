package com.company.taskmanagementplatform.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * The constraints the projects schema relies on, exercised against a real PostgreSQL.
 *
 * <p>Written at the SQL level on purpose, like {@code IdentitySchemaIT} and {@code TeamSchemaIT}.
 * These rules exist so that a mistake in the service layer cannot corrupt the data, so driving them
 * through the service layer would prove the wrong thing.
 */
class ProjectSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void refusesAnOwnerWhoIsNotAMemberOfTheWorkspace() {
        Fixture fixture = workspaceWithMember();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));

        assertThatThrownBy(() -> insertProject(fixture, uniqueKey(), uniqueName(), outsider.id(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsAnOwnerWhoIs() {
        Fixture fixture = workspaceWithMember();

        assertThatCode(() -> insertProject(fixture, uniqueKey(), uniqueName(), fixture.userId(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesATeamFromAnotherWorkspace() {
        Fixture mine = workspaceWithMember();
        Fixture theirs = workspaceWithMember();
        UUID foreignTeam = insertTeam(theirs);

        assertThatThrownBy(() -> insertProject(mine, uniqueKey(), uniqueName(), null, foreignTeam))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAProjectMemberWhoIsNotInTheWorkspace() {
        Fixture fixture = workspaceWithMember();
        UUID projectId = insertProject(fixture, uniqueKey(), uniqueName(), null, null);
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));

        assertThatThrownBy(() -> insertProjectMember(projectId, fixture.workspaceId(), outsider.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAProjectMemberRecordedAgainstTheWrongWorkspace() {
        Fixture mine = workspaceWithMember();
        Fixture theirs = workspaceWithMember();
        UUID projectId = insertProject(mine, uniqueKey(), uniqueName(), null, null);

        assertThatThrownBy(() -> insertProjectMember(projectId, theirs.workspaceId(), theirs.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTwoLiveProjectsWithTheSameKeyHoweverItIsCased() {
        Fixture fixture = workspaceWithMember();
        String key = uniqueKey();
        insertProject(fixture, key, uniqueName(), null, null);

        assertThatThrownBy(() -> insertProject(
                        fixture, key.toLowerCase(java.util.Locale.ROOT), uniqueName(), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTwoLiveProjectsWithTheSameName() {
        Fixture fixture = workspaceWithMember();
        String name = uniqueName();
        insertProject(fixture, uniqueKey(), name, null, null);

        assertThatThrownBy(() -> insertProject(
                        fixture, uniqueKey(), "  " + name.toUpperCase(java.util.Locale.ROOT), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameKeyInAnotherWorkspace() {
        Fixture mine = workspaceWithMember();
        Fixture theirs = workspaceWithMember();
        String key = uniqueKey();
        insertProject(mine, key, uniqueName(), null, null);

        assertThatCode(() -> insertProject(theirs, key, uniqueName(), null, null)).doesNotThrowAnyException();
    }

    @Test
    void allowsAKeyToBeReusedOnceTheProjectIsRemoved() {
        Fixture fixture = workspaceWithMember();
        String key = uniqueKey();
        UUID projectId = insertProject(fixture, key, uniqueName(), null, null);
        jdbc.update("UPDATE projects SET deleted_at = now() WHERE id = ?", projectId);

        assertThatCode(() -> insertProject(fixture, key, uniqueName(), null, null)).doesNotThrowAnyException();
    }

    @Test
    void refusesAStatusOutsideTheKnownSet() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO projects (workspace_id, key, name, status, created_by_user_id)
                        VALUES (?, ?, ?, 'CANCELLED', ?)
                        """,
                        fixture.workspaceId(),
                        uniqueKey(),
                        uniqueName(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAPriorityOutsideTheKnownSet() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO projects (workspace_id, key, name, priority, created_by_user_id)
                        VALUES (?, ?, ?, 'URGENT', ?)
                        """,
                        fixture.workspaceId(),
                        uniqueKey(),
                        uniqueName(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAProjectThatEndsBeforeItStarts() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO projects (workspace_id, key, name, start_date, end_date, created_by_user_id)
                        VALUES (?, ?, ?, DATE '2026-06-01', DATE '2026-05-01', ?)
                        """,
                        fixture.workspaceId(),
                        uniqueKey(),
                        uniqueName(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesProgressOutsideZeroToOneHundred() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO projects (workspace_id, key, name, progress, created_by_user_id)
                        VALUES (?, ?, ?, 101, ?)
                        """,
                        fixture.workspaceId(),
                        uniqueKey(),
                        uniqueName(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAKeyThatIsNotTheAgreedShape() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> insertProject(fixture, "lower", uniqueName(), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTheSamePersonTwiceOnOneProject() {
        Fixture fixture = workspaceWithMember();
        UUID projectId = insertProject(fixture, uniqueKey(), uniqueName(), null, null);
        insertProjectMember(projectId, fixture.workspaceId(), fixture.userId());

        assertThatThrownBy(() -> insertProjectMember(projectId, fixture.workspaceId(), fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesToRemoveAWorkspaceMemberWhoStillOwnsAProject() {
        // The rule the cleanup listener exists to satisfy.
        Fixture fixture = workspaceWithMember();
        insertProject(fixture, uniqueKey(), uniqueName(), fixture.userId(), null);

        assertThatThrownBy(() -> jdbc.update(
                        "DELETE FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                        fixture.workspaceId(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesToRemoveAWorkspaceMemberWhoIsStillOnAProject() {
        Fixture fixture = workspaceWithMember();
        UUID projectId = insertProject(fixture, uniqueKey(), uniqueName(), null, null);
        insertProjectMember(projectId, fixture.workspaceId(), fixture.userId());

        assertThatThrownBy(() -> jdbc.update(
                        "DELETE FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                        fixture.workspaceId(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removesTheRosterAndTagsWhenTheProjectRowItselfIsDeleted() {
        Fixture fixture = workspaceWithMember();
        UUID projectId = insertProject(fixture, uniqueKey(), uniqueName(), null, null);
        insertProjectMember(projectId, fixture.workspaceId(), fixture.userId());
        UUID labelId = insertLabel(fixture.workspaceId(), uniqueName());
        insertProjectLabel(projectId, labelId, fixture.workspaceId());

        jdbc.update("DELETE FROM projects WHERE id = ?", projectId);

        assertThat(count("SELECT count(*) FROM project_members WHERE project_id = ?", projectId))
                .isZero();
        assertThat(count("SELECT count(*) FROM project_labels WHERE project_id = ?", projectId))
                .isZero();
    }

    @Test
    void refusesTaggingAProjectWithAnotherWorkspacesLabel() {
        Fixture mine = workspaceWithMember();
        Fixture theirs = workspaceWithMember();
        UUID projectId = insertProject(mine, uniqueKey(), uniqueName(), null, null);
        UUID foreignLabel = insertLabel(theirs.workspaceId(), uniqueName());

        assertThatThrownBy(() -> insertProjectLabel(projectId, foreignLabel, mine.workspaceId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTwoLabelsInAWorkspaceWhoseNamesDifferOnlyInCase() {
        Fixture fixture = workspaceWithMember();
        String name = uniqueName();
        insertLabel(fixture.workspaceId(), name);

        assertThatThrownBy(() -> insertLabel(fixture.workspaceId(), name.toUpperCase(java.util.Locale.ROOT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- helpers ----------------------------------------------------------

    private Fixture workspaceWithMember() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Projects", uniqueSlug("projects"), owner.id());
        UserAccount member = fixtures.verifiedUser(uniqueEmail("member"));
        fixtures.addMember(workspace.id(), member.id(), "EMPLOYEE");
        return new Fixture(workspace.id(), member.id());
    }

    private UUID insertProject(Fixture fixture, String key, String name, UUID ownerUserId, UUID teamId) {
        return jdbc.queryForObject(
                """
                INSERT INTO projects (workspace_id, key, name, owner_user_id, team_id, created_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                UUID.class,
                fixture.workspaceId(),
                key,
                name,
                ownerUserId,
                teamId,
                fixture.userId());
    }

    private UUID insertTeam(Fixture fixture) {
        return jdbc.queryForObject(
                "INSERT INTO teams (workspace_id, name, created_by_user_id) VALUES (?, ?, ?) RETURNING id",
                UUID.class,
                fixture.workspaceId(),
                uniqueName(),
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

    private void insertProjectLabel(UUID projectId, UUID labelId, UUID workspaceId) {
        jdbc.update(
                "INSERT INTO project_labels (project_id, label_id, workspace_id) VALUES (?, ?, ?)",
                projectId,
                labelId,
                workspaceId);
    }

    private Integer count(String sql, Object argument) {
        return jdbc.queryForObject(sql, Integer.class, argument);
    }

    private static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static String uniqueName() {
        return "Project " + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(UUID workspaceId, UUID userId) {}
}
