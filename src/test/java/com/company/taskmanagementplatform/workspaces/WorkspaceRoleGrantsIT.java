package com.company.taskmanagementplatform.workspaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * Holds {@link SystemRole} together with the rows a new workspace actually receives.
 *
 * <p>The grants for a workspace role exist twice, and the pair is easy to break. New workspaces get
 * theirs from {@code SystemRole} when {@code WorkspaceRoleSeeder} runs; workspaces that already
 * existed when a phase added permissions got theirs from that phase's backfill. Two workspaces
 * created either side of a migration have to be able to do the same things, and nothing else would
 * notice if they could not.
 *
 * <p>Lives in this package because {@code SystemRole} is package-private, which is deliberate: the
 * grants are the workspace module's business and no other module should be reading them.
 */
class WorkspaceRoleGrantsIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void aNewWorkspaceReceivesExactlyWhatTheEnumDeclares() {
        UUID workspaceId = freshWorkspace();

        for (SystemRole role : SystemRole.values()) {
            assertThat(grantedTo(workspaceId, role.slug()))
                    .as("permissions granted to %s", role.slug())
                    .containsExactlyInAnyOrderElementsOf(role.permissionCodes());
        }
    }

    @Test
    void noWorkspaceRoleAnywhereHoldsMoreThanTheEnumDeclares() {
        // Deliberately one-directional. The suite shares one database, and
        // IdentitySchemaIT hand-inserts role rows with SQL and no grants at all to
        // exercise constraints, so requiring an exact match across every row would
        // fail on those by design.
        //
        // The direction that does hold everywhere is the one worth guarding: a
        // backfill that granted too much, or granted to the wrong slug, shows up
        // here as a permission nothing declared.
        freshWorkspace();

        for (SystemRole role : SystemRole.values()) {
            List<UUID> roleIds = jdbc.queryForList(
                    "SELECT id FROM roles WHERE scope = 'WORKSPACE' AND slug = ?", UUID.class, role.slug());

            assertThat(roleIds).as("workspace roles named %s", role.slug()).isNotEmpty();

            for (UUID roleId : roleIds) {
                assertThat(role.permissionCodes())
                        .as("permissions on role %s (%s)", roleId, role.slug())
                        .containsAll(grantedToRole(roleId));
            }
        }
    }

    @Test
    void noWorkspaceRoleReachesAcrossIntoPlatformAdministration() {
        // The phase added a workspace-level archive permission next to the
        // platform-level delete one. Granting the wrong one to ADMIN would hand
        // every workspace administrator the ability to remove their workspace.
        UUID workspaceId = freshWorkspace();

        for (SystemRole role : SystemRole.values()) {
            assertThat(grantedTo(workspaceId, role.slug()))
                    .as("permissions granted to %s", role.slug())
                    .doesNotContain(
                            Permissions.WORKSPACE_DELETE,
                            Permissions.WORKSPACE_CREATE,
                            Permissions.USER_DELETE);
        }
    }

    @Test
    void anAdministratorMayRunTeamsAndArchiveTheWorkspace() {
        UUID workspaceId = freshWorkspace();

        assertThat(grantedTo(workspaceId, "ADMIN"))
                .contains(
                        Permissions.WORKSPACE_ARCHIVE,
                        Permissions.TEAM_CREATE,
                        Permissions.TEAM_UPDATE,
                        Permissions.TEAM_DELETE,
                        Permissions.TEAM_MANAGE_MEMBERS,
                        Permissions.TEAM_MANAGE_ANY);
    }

    @Test
    void aTeamLeadManagesTeamsButOnlyTheOnesTheyLead() {
        // The scope half of the rule: they hold the two management codes and not
        // the workspace-wide grant that would widen them to every team.
        UUID workspaceId = freshWorkspace();
        List<String> granted = grantedTo(workspaceId, "TEAM_LEAD");

        assertThat(granted).contains(Permissions.TEAM_UPDATE, Permissions.TEAM_MANAGE_MEMBERS);
        assertThat(granted)
                .doesNotContain(Permissions.TEAM_MANAGE_ANY, Permissions.TEAM_CREATE, Permissions.TEAM_DELETE);
    }

    @Test
    void anEmployeeMaySeeTeamsAndNothingMore() {
        UUID workspaceId = freshWorkspace();
        List<String> granted = grantedTo(workspaceId, "EMPLOYEE");

        assertThat(granted).contains(Permissions.TEAM_READ);
        assertThat(granted)
                .doesNotContain(
                        Permissions.TEAM_CREATE,
                        Permissions.TEAM_UPDATE,
                        Permissions.TEAM_DELETE,
                        Permissions.TEAM_MANAGE_MEMBERS,
                        Permissions.TEAM_MANAGE_ANY,
                        Permissions.WORKSPACE_ARCHIVE);
    }

    @Test
    void aNewWorkspaceStartsWithAnEmployeeDefaultRole() {
        // The setting an invitation falls back to. Null here would make the first
        // invitation that omits a role fail for no reason a user could act on.
        UUID workspaceId = freshWorkspace();

        String slug = jdbc.queryForObject(
                """
                SELECT r.slug FROM workspaces w
                JOIN roles r ON r.id = w.default_role_id
                WHERE w.id = ?
                """,
                String.class,
                workspaceId);

        assertThat(slug).isEqualTo("EMPLOYEE");
    }

    private UUID freshWorkspace() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("grants"));
        WorkspaceResponse workspace = fixtures.workspace("Grants", uniqueSlug("grants"), owner.id());
        return workspace.id();
    }

    private List<String> grantedTo(UUID workspaceId, String roleSlug) {
        return jdbc.queryForList(
                """
                SELECT p.code
                FROM role_permissions rp
                JOIN permissions p ON p.id = rp.permission_id
                JOIN roles r ON r.id = rp.role_id
                WHERE r.workspace_id = ? AND r.slug = ?
                """,
                String.class,
                workspaceId,
                roleSlug);
    }

    private List<String> grantedToRole(UUID roleId) {
        return jdbc.queryForList(
                """
                SELECT p.code
                FROM role_permissions rp
                JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                """,
                String.class,
                roleId);
    }
}
