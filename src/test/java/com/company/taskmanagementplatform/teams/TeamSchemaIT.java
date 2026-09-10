package com.company.taskmanagementplatform.teams;

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
 * The constraints the teams schema relies on, exercised against a real PostgreSQL.
 *
 * <p>Written at the SQL level on purpose, like {@code IdentitySchemaIT}. These rules exist so that a
 * mistake in the service layer cannot corrupt the data, so driving them through the service layer
 * would prove the wrong thing. Every write below is one the database must refuse whatever the
 * application believes about it.
 *
 * <p>The people and workspaces are still built through the services, because setting those up with
 * raw SQL is how a suite ends up asserting against a shape the application would never produce.
 */
class TeamSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void refusesATeamLedBySomebodyWhoIsNotAMemberOfItsWorkspace() {
        Fixture fixture = workspaceWithMember();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));

        assertThatThrownBy(() -> insertTeam(fixture.workspaceId(), uniqueSlug("Team"), outsider.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsATeamLedBySomebodyWhoIs() {
        Fixture fixture = workspaceWithMember();

        assertThatCode(() -> insertTeam(fixture.workspaceId(), uniqueSlug("Team"), fixture.userId()))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesATeamMemberWhoIsNotAMemberOfTheWorkspace() {
        Fixture fixture = workspaceWithMember();
        UUID teamId = insertTeam(fixture.workspaceId(), uniqueSlug("Team"), null);
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));

        assertThatThrownBy(() -> insertTeamMember(teamId, fixture.workspaceId(), outsider.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesATeamMemberRecordedAgainstTheWrongWorkspace() {
        // The composite key into teams (id, workspace_id) is what makes this
        // impossible rather than merely unlikely.
        Fixture mine = workspaceWithMember();
        Fixture theirs = workspaceWithMember();
        UUID teamId = insertTeam(mine.workspaceId(), uniqueSlug("Team"), null);

        assertThatThrownBy(() -> insertTeamMember(teamId, theirs.workspaceId(), theirs.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTwoLiveTeamsWhoseNamesDifferOnlyInCaseOrSpacing() {
        Fixture fixture = workspaceWithMember();
        String name = uniqueSlug("Platform");
        insertTeam(fixture.workspaceId(), name, null);

        assertThatThrownBy(() -> insertTeam(fixture.workspaceId(), "  " + name.toUpperCase(java.util.Locale.ROOT), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameNameInAnotherWorkspace() {
        Fixture mine = workspaceWithMember();
        Fixture theirs = workspaceWithMember();
        String name = uniqueSlug("Platform");
        insertTeam(mine.workspaceId(), name, null);

        assertThatCode(() -> insertTeam(theirs.workspaceId(), name, null)).doesNotThrowAnyException();
    }

    @Test
    void allowsANameToBeReusedOnceTheTeamIsRemoved() {
        // The unique index is partial, so deleting a team should not reserve its
        // name for ever.
        Fixture fixture = workspaceWithMember();
        String name = uniqueSlug("Platform");
        UUID teamId = insertTeam(fixture.workspaceId(), name, null);
        jdbc.update("UPDATE teams SET deleted_at = now() WHERE id = ?", teamId);

        assertThatCode(() -> insertTeam(fixture.workspaceId(), name, null)).doesNotThrowAnyException();
    }

    @Test
    void refusesAStatusOutsideTheKnownSet() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO teams (workspace_id, name, status, created_by_user_id)
                        VALUES (?, ?, 'RETIRED', ?)
                        """,
                        fixture.workspaceId(),
                        uniqueSlug("Team"),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesATeamWithABlankName() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> insertTeam(fixture.workspaceId(), "   ", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTheSamePersonTwiceInOneTeam() {
        Fixture fixture = workspaceWithMember();
        UUID teamId = insertTeam(fixture.workspaceId(), uniqueSlug("Team"), null);
        insertTeamMember(teamId, fixture.workspaceId(), fixture.userId());

        assertThatThrownBy(() -> insertTeamMember(teamId, fixture.workspaceId(), fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesToRemoveAWorkspaceMemberWhoStillLeadsATeam() {
        // The rule the cleanup listener exists to satisfy. If it ever stopped
        // running, this is the error the removal would produce rather than a
        // roster quietly pointing at somebody who left.
        Fixture fixture = workspaceWithMember();
        insertTeam(fixture.workspaceId(), uniqueSlug("Team"), fixture.userId());

        assertThatThrownBy(() -> jdbc.update(
                        "DELETE FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                        fixture.workspaceId(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesToRemoveAWorkspaceMemberWhoIsStillInATeam() {
        Fixture fixture = workspaceWithMember();
        UUID teamId = insertTeam(fixture.workspaceId(), uniqueSlug("Team"), null);
        insertTeamMember(teamId, fixture.workspaceId(), fixture.userId());

        assertThatThrownBy(() -> jdbc.update(
                        "DELETE FROM workspace_members WHERE workspace_id = ? AND user_id = ?",
                        fixture.workspaceId(),
                        fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removesTheRosterWhenTheTeamRowItselfIsDeleted() {
        // ON DELETE CASCADE on the composite key, so a hard delete of a team never
        // leaves join rows pointing at nothing.
        Fixture fixture = workspaceWithMember();
        UUID teamId = insertTeam(fixture.workspaceId(), uniqueSlug("Team"), null);
        insertTeamMember(teamId, fixture.workspaceId(), fixture.userId());

        jdbc.update("DELETE FROM teams WHERE id = ?", teamId);

        Integer left = jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ?", Integer.class, teamId);
        org.assertj.core.api.Assertions.assertThat(left).isZero();
    }

    @Test
    void refusesAWorkspaceWhoseDefaultRoleBelongsToAnotherWorkspace() {
        // Carried over from the workspace half of this phase, and the same trick:
        // the default role is keyed to roles (id, workspace_id).
        Fixture mine = workspaceWithMember();
        Fixture theirs = workspaceWithMember();
        UUID foreignRoleId = fixtures.roleId(theirs.workspaceId(), "ADMIN");

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE workspaces SET default_role_id = ? WHERE id = ?", foreignRoleId, mine.workspaceId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAWorkspaceThatCallsItselfArchivedWithNoTimestamp() {
        Fixture fixture = workspaceWithMember();

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE workspaces SET status = 'ARCHIVED' WHERE id = ?", fixture.workspaceId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture workspaceWithMember() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Teams", uniqueSlug("teams"), owner.id());
        UserAccount member = fixtures.verifiedUser(uniqueEmail("member"));
        fixtures.addMember(workspace.id(), member.id(), "EMPLOYEE");
        return new Fixture(workspace.id(), member.id());
    }

    private UUID insertTeam(UUID workspaceId, String name, UUID leadUserId) {
        UUID creator = jdbc.queryForObject(
                "SELECT user_id FROM workspace_members WHERE workspace_id = ? LIMIT 1", UUID.class, workspaceId);

        return jdbc.queryForObject(
                """
                INSERT INTO teams (workspace_id, name, lead_user_id, created_by_user_id)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                UUID.class,
                workspaceId,
                name,
                leadUserId,
                creator);
    }

    private void insertTeamMember(UUID teamId, UUID workspaceId, UUID userId) {
        jdbc.update(
                "INSERT INTO team_members (team_id, workspace_id, user_id) VALUES (?, ?, ?)",
                teamId,
                workspaceId,
                userId);
    }

    private record Fixture(UUID workspaceId, UUID userId) {}
}
