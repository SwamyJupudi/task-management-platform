package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * The constraints the identity schema relies on, exercised against a real PostgreSQL.
 *
 * <p>Written at the SQL level on purpose. These rules exist so that a mistake in the service layer
 * cannot corrupt the data, so testing them through the service layer would prove the wrong thing.
 * Each of these writes is one the database must refuse whatever the application believes.
 */
class IdentitySchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.company.taskmanagementplatform.support.IdentityFixtures fixtures;

    @Test
    void refusesTwoAddressesThatDifferOnlyInCase() {
        String email = uniqueEmail("case-test");
        insertUser(email);

        assertThatThrownBy(() -> insertUser(email.toUpperCase(java.util.Locale.ROOT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsAnAddressToBeReusedOnceTheAccountIsRemoved() {
        // The unique index is partial. Removing somebody should not reserve their
        // address for ever.
        String email = uniqueEmail("reuse-test");
        UUID id = insertUser(email);
        jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", id);

        assertThatCode(() -> insertUser(email)).doesNotThrowAnyException();
    }

    @Test
    void refusesAPlatformRoleThatNamesAWorkspace() {
        UUID workspaceId = insertWorkspace();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO roles (workspace_id, slug, name, scope)
                        VALUES (?, 'BOGUS', 'Bogus', 'PLATFORM')
                        """,
                        workspaceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAWorkspaceRoleWithNoWorkspace() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO roles (workspace_id, slug, name, scope) VALUES (NULL, ?, 'Bogus', 'WORKSPACE')",
                        uniqueSlug("BOGUS")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesASecondPlatformRoleWithTheSameSlug() {
        // The case a plain unique index would miss: it treats every null workspace
        // as distinct, so two SUPER_ADMIN roles would both be allowed.
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO roles (workspace_id, slug, name, scope)
                        VALUES (NULL, 'SUPER_ADMIN', 'Impostor', 'PLATFORM')
                        """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAMemberHoldingARoleFromAnotherWorkspace() {
        // The composite foreign key. Without it this would be a service-layer rule
        // that review has to keep catching.
        UUID firstWorkspace = insertWorkspace();
        UUID secondWorkspace = insertWorkspace();
        UUID foreignRole = roleIdIn(secondWorkspace, "ADMIN");
        UUID userId = insertUser(uniqueEmail("cross-role"));

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO workspace_members (workspace_id, user_id, role_id) VALUES (?, ?, ?)",
                        firstWorkspace,
                        userId,
                        foreignRole))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAMemberHoldingThePlatformRole() {
        // Falls out of the same key: a platform role has a null workspace and so
        // cannot satisfy it. No workspace member can ever be SUPER_ADMIN.
        UUID workspaceId = insertWorkspace();
        UUID userId = insertUser(uniqueEmail("super-member"));
        UUID superAdminRole =
                jdbc.queryForObject("SELECT id FROM roles WHERE slug = 'SUPER_ADMIN' AND scope = 'PLATFORM'", UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO workspace_members (workspace_id, user_id, role_id) VALUES (?, ?, ?)",
                        workspaceId,
                        userId,
                        superAdminRole))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTheSamePersonTwiceInOneWorkspace() {
        UUID workspaceId = insertWorkspace();
        UUID userId = insertUser(uniqueEmail("duplicate-member"));
        UUID roleId = roleIdIn(workspaceId, "EMPLOYEE");

        jdbc.update(
                "INSERT INTO workspace_members (workspace_id, user_id, role_id) VALUES (?, ?, ?)",
                workspaceId,
                userId,
                roleId);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO workspace_members (workspace_id, user_id, role_id) VALUES (?, ?, ?)",
                        workspaceId,
                        userId,
                        roleId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesARevokedTokenWithNoReason() {
        UUID userId = insertUser(uniqueEmail("revocation"));

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO refresh_tokens (user_id, session_id, token_hash, expires_at, revoked_at)
                        VALUES (?, gen_random_uuid(), gen_random_uuid()::text, now() + interval '1 day', now())
                        """,
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAPermissionCodeThatContradictsItsParts() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO permissions (code, resource, action, description)
                        VALUES ('thing:read', 'other', 'write', 'Inconsistent')
                        """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedsEveryWorkspaceRoleWhenAWorkspaceIsCreatedThroughTheApplication() {
        // Guards the seeder rather than the schema, but belongs with the rest: a
        // workspace without its roles cannot have members at all. Created through
        // the service, since creating it with SQL here would only test the SQL.
        var creator = fixtures.verifiedUser(uniqueEmail("seeder"));
        var workspace = fixtures.workspace("Seeded", uniqueSlug("seeded"), creator.id());

        List<String> slugs = jdbc.queryForList(
                "SELECT slug FROM roles WHERE workspace_id = ? ORDER BY slug", String.class, workspace.id());

        assertThat(slugs).containsExactly("ADMIN", "EMPLOYEE", "TEAM_LEAD");
    }

    @Test
    void givesEachWorkspaceItsOwnRoleRows() {
        // Roles are per workspace so that one administrator's edits cannot reach
        // another workspace.
        var creator = fixtures.verifiedUser(uniqueEmail("separate"));
        var first = fixtures.workspace("First", uniqueSlug("first"), creator.id());
        var second = fixtures.workspace("Second", uniqueSlug("second"), creator.id());

        assertThat(fixtures.roleId(first.id(), "ADMIN")).isNotEqualTo(fixtures.roleId(second.id(), "ADMIN"));
    }

    private UUID insertUser(String email) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, first_name, last_name, status, email_verified_at)
                VALUES (?, 'hash', 'Test', 'Person', 'ACTIVE', now())
                RETURNING id
                """,
                UUID.class,
                email);
    }

    /** Creates a workspace and its roles the way the application does, through SQL for speed. */
    private UUID insertWorkspace() {
        UUID creator = insertUser(uniqueEmail("workspace-creator"));
        UUID workspaceId = jdbc.queryForObject(
                "INSERT INTO workspaces (name, slug, created_by_user_id) VALUES ('Test', ?, ?) RETURNING id",
                UUID.class,
                uniqueSlug("ws"),
                creator);

        for (String slug : List.of("ADMIN", "TEAM_LEAD", "EMPLOYEE")) {
            jdbc.update(
                    "INSERT INTO roles (workspace_id, slug, name, scope, is_system) VALUES (?, ?, ?, 'WORKSPACE', true)",
                    workspaceId,
                    slug,
                    slug);
        }
        return workspaceId;
    }

    private UUID roleIdIn(UUID workspaceId, String slug) {
        return jdbc.queryForObject(
                "SELECT id FROM roles WHERE workspace_id = ? AND slug = ?", UUID.class, workspaceId, slug);
    }

}
