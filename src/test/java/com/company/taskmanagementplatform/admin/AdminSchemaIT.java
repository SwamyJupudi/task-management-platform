package com.company.taskmanagementplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * What {@code V10} did to the schema, asserted against a real PostgreSQL.
 *
 * <p>Written at the SQL level on purpose, like {@code IdentitySchemaIT}, {@code TeamSchemaIT},
 * {@code ProjectSchemaIT}, {@code TaskSchemaIT} and {@code ReportSchemaIT}. A nullable column, a
 * check constraint and a trigger are properties of the schema rather than of the service layer, and
 * driving them through the service layer would prove the wrong thing.
 *
 * <p><strong>The central assertion is the last one.</strong> This migration altered the one table
 * the platform guarantees cannot be modified, and the guarantee is a trigger. Dropping a NOT NULL
 * and swapping a check constraint should not disturb it, because it is a row trigger on UPDATE and
 * DELETE and DDL does not fire it. That needs to be true rather than believed.
 *
 * <p>Like its counterparts, it asserts that the indexes exist rather than that the planner uses
 * them. An {@code EXPLAIN} assertion reads stronger and is weaker: a planner is right to choose a
 * sequential scan over fifty rows, so such a test fails on data volume rather than on a defect.
 */
class AdminSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // --- the audit table's new shape ---------------------------------------

    @Test
    void theAuditWorkspaceIsNullableSoPlatformActionsCanBeRecordedAtAll() {
        // The whole reason V10 touched an existing table. Deactivating an account
        // and granting the platform role happen outside any workspace, so before
        // this they could not be written to the audit trail at all, and were not.
        assertThat(isNullable("activity_logs", "workspace_id")).isTrue();
    }

    @Test
    void theEntityTypeCheckAcceptsTheTwoAdministrativeKinds() {
        UUID subject = UUID.randomUUID();

        insertPlatformRow("USER", subject, "user.deactivated");
        insertPlatformRow("ROLE", subject, "role.permissions_changed");

        assertThat(countRowsFor(subject)).isEqualTo(2);
    }

    @Test
    void theEntityTypeCheckStillRefusesSomethingInvented() {
        // Widening a constraint is only safe if it stays a constraint.
        assertThatThrownBy(() -> insertPlatformRow("GADGET", UUID.randomUUID(), "gadget.created"))
                .hasMessageContaining("activity_logs_entity_type_check");
    }

    @Test
    void aPlatformRowCarriesNoWorkspaceAndIsInvisibleToAWorkspaceBrowse() {
        UUID subject = UUID.randomUUID();
        insertPlatformRow("USER", subject, "user.unlocked");

        // The invariant every workspace-scoped query relies on since the column
        // became nullable: filtering by workspace_id excludes these rows entirely.
        Long visibleToAnyWorkspace = jdbc.queryForObject(
                "SELECT count(*) FROM activity_logs WHERE entity_id = ? AND workspace_id IS NOT NULL",
                Long.class,
                subject);

        assertThat(visibleToAnyWorkspace).isZero();
    }

    @Test
    void aPlatformRowIsStillAppendOnly() {
        // The assertion this class exists for. V10 altered the one table the
        // platform promises cannot be edited, and the promise is a trigger.
        UUID subject = UUID.randomUUID();
        insertPlatformRow("USER", subject, "user.deleted");

        assertThatThrownBy(() ->
                        jdbc.update("UPDATE activity_logs SET action = 'tampered' WHERE entity_id = ?", subject))
                .hasMessageContaining("append only");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM activity_logs WHERE entity_id = ?", subject))
                .hasMessageContaining("append only");
    }

    // --- permissions --------------------------------------------------------

    @Test
    void bothNewPermissionsExistAndAreMappedToThePlatformAdministrator() {
        // The standing obligation on every migration that adds a permission. There
        // is no bypass branch for SUPER_ADMIN anywhere, so an unmapped permission
        // is one it silently does not hold.
        for (String code : List.of("admin:read_system", "platform_role:assign")) {
            assertThat(mappedToSuperAdmin(code)).as("%s mapped to SUPER_ADMIN", code).isTrue();
        }
    }

    @Test
    void neitherNewPermissionReachesAnyWorkspaceRole() {
        // The third answer the backfill obligation has had. V4-V7 backfilled by
        // slug; V8 and V9 added no permission at all; these two name platform
        // administration and no workspace role should ever hold either. The
        // precedent is workspace:delete, which has sat in the catalog since V3
        // mapped to SUPER_ADMIN and granted to no workspace role.
        Long workspaceGrants = jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE r.scope = 'WORKSPACE'
                  AND p.code IN ('admin:read_system', 'platform_role:assign')
                """,
                Long.class);

        assertThat(workspaceGrants).isZero();
    }

    // --- indexes ------------------------------------------------------------

    @Test
    void indexesThePlatformAuditBrowsePartiallyOnTheNullWorkspace() {
        // activity_logs_workspace_created_idx leads with workspace_id, so it cannot
        // serve an IS NULL scan ordered by time. Partial on the predicate, this one
        // holds only platform rows and is tiny beside the workspace history.
        assertThat(definitionOf("activity_logs_platform_created_idx"))
                .contains("created_at")
                .contains("workspace_id IS NULL");
    }

    @Test
    void theAccountStatusBreakdownIsServedByTheIndexV2AlreadyMade() {
        // V10 deliberately creates nothing for this. The status breakdown is a
        // GROUP BY over the whole users table with no other predicate, and V2's
        // index is already exactly that shape, so a second one would have been a
        // duplicate under another name. Asserted here anyway, because this phase
        // is the first thing that depends on it.
        assertThat(definitionOf("users_status_idx")).contains("status").contains("deleted_at IS NULL");
    }

    @Test
    void indexesLockedAccountsPartiallySoItHoldsAlmostNothing() {
        assertThat(definitionOf("users_locked_until_idx"))
                .contains("locked_until")
                .contains("locked_until IS NOT NULL");
    }

    @Test
    void addsNoTableAndNoMaterializedView() {
        // The admin panel is composition over what phases two to eight already
        // store, plus four account verbs writing columns that already existed.
        // A rollup would be a second copy of the truth that can go stale.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_matviews WHERE schemaname = current_schema()", Long.class))
                .isZero();

        assertThat(jdbc.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() "
                                + "AND tablename LIKE 'admin%'",
                        String.class))
                .isEmpty();
    }

    // --- helpers ------------------------------------------------------------

    private void insertPlatformRow(String entityType, UUID entityId, String action) {
        jdbc.update(
                """
                INSERT INTO activity_logs (workspace_id, actor_user_id, action, entity_type, entity_id)
                VALUES (NULL, NULL, ?, ?, ?)
                """,
                action,
                entityType,
                entityId);
    }

    private long countRowsFor(UUID entityId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM activity_logs WHERE entity_id = ?", Long.class, entityId);
        return count == null ? 0L : count;
    }

    private boolean isNullable(String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT NOT a.attnotnull
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                WHERE c.relname = ? AND a.attname = ? AND a.attnum > 0
                """,
                Boolean.class,
                table,
                column));
    }

    private boolean mappedToSuperAdmin(String code) {
        Long found = jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE r.slug = 'SUPER_ADMIN' AND r.scope = 'PLATFORM' AND p.code = ?
                """,
                Long.class,
                code);
        return found != null && found == 1L;
    }

    private String definitionOf(String indexName) {
        List<String> found = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                String.class,
                indexName);

        assertThat(found).as("index %s should exist", indexName).hasSize(1);
        return found.get(0);
    }
}
