package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * The privilege half of the append-only audit trail, added by {@code V13}.
 *
 * <p>{@code V7} built the trigger and recorded what it could not do: a trigger can be dropped or
 * disabled by the role that owns the table, and until this phase that role was the application's
 * own. {@code V13} answers that by putting the runtime privilege set on a {@code NOLOGIN} group
 * role, {@code task_platform_app}, which holds {@code SELECT} and {@code INSERT} on
 * {@code activity_logs} and nothing else.
 *
 * <p>The first tests read the grant state, which is what the migration is directly responsible for.
 * The last one is the one worth having: it creates a login role, grants it the group, connects as
 * it, and checks that an {@code UPDATE} is refused by PostgreSQL rather than by the trigger. A grant
 * that looks right in {@code pg_catalog} and does not bite is the failure this is guarding against.
 *
 * <p>Note what this cannot assert. The application under test connects as the container's superuser,
 * and a superuser bypasses every grant, so nothing here proves the running application is
 * constrained — that is done by the deployment pointing {@code DB_USERNAME} at a member of this
 * group, which {@code docs/deployment.md} sets out. What is provable here is that the group carries
 * the right privileges and that they are enforced against somebody holding only them.
 */
class AuditRoleSeparationIT extends AbstractIntegrationTest {

    private static final String GROUP_ROLE = "task_platform_app";

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Unique per test, because the container is shared across the whole suite and a role is
     * cluster-wide rather than database-scoped.
     */
    private final String loginRole = "tmp_app_probe_" + UUID.randomUUID().toString().replace("-", "");

    @AfterEach
    void dropTheProbeRole() {
        // Guarded, because most tests in this class never create the role and
        // DROP OWNED BY refuses a name that does not exist. DROP OWNED BY comes
        // first for the one that does: a role cannot be dropped while anything in
        // the database still depends on it, and that statement removes the
        // membership and any privilege the probe picked up.
        jdbc.execute(
                """
                DO $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '%s') THEN
                        DROP OWNED BY %s;
                        DROP ROLE %s;
                    END IF;
                END
                $$
                """
                        .formatted(loginRole, loginRole, loginRole));
    }

    @Test
    void theRuntimeGroupRoleExistsAndCannotBeLoggedInAs() {
        Boolean canLogIn = jdbc.queryForObject(
                "SELECT rolcanlogin FROM pg_roles WHERE rolname = ?", Boolean.class, GROUP_ROLE);

        // Not merely present: NOLOGIN. Nothing authenticates as the privilege set
        // itself, which is why the migration needs no password and therefore puts
        // none in version control.
        assertThat(canLogIn).isFalse();
    }

    @Test
    void theGroupMayReadAndAppendToTheAuditTrailAndNothingElse() {
        assertThat(privilege(GROUP_ROLE, "activity_logs", "SELECT")).isTrue();
        assertThat(privilege(GROUP_ROLE, "activity_logs", "INSERT")).isTrue();

        assertThat(privilege(GROUP_ROLE, "activity_logs", "UPDATE")).isFalse();
        assertThat(privilege(GROUP_ROLE, "activity_logs", "DELETE")).isFalse();
        // TRUNCATE separately, because it is not covered by DELETE, fires no row
        // trigger, and would empty the table in one statement while V7's trigger
        // watched and said nothing.
        assertThat(privilege(GROUP_ROLE, "activity_logs", "TRUNCATE")).isFalse();
    }

    @Test
    void theRevokeIsTargetedRatherThanAGeneralLossOfWriteAccess() {
        // The same role on an ordinary table. Without this the previous test would
        // pass just as well if V13 had granted nothing at all, and an application
        // that cannot write anything is not the thing being asserted.
        assertThat(privilege(GROUP_ROLE, "tasks", "SELECT")).isTrue();
        assertThat(privilege(GROUP_ROLE, "tasks", "INSERT")).isTrue();
        assertThat(privilege(GROUP_ROLE, "tasks", "UPDATE")).isTrue();
        assertThat(privilege(GROUP_ROLE, "tasks", "DELETE")).isTrue();
    }

    @Test
    void theGroupCannotRewriteTheMigrationHistory() {
        assertThat(privilege(GROUP_ROLE, "flyway_schema_history", "SELECT")).isTrue();
        assertThat(privilege(GROUP_ROLE, "flyway_schema_history", "INSERT")).isFalse();
        assertThat(privilege(GROUP_ROLE, "flyway_schema_history", "UPDATE")).isFalse();
        assertThat(privilege(GROUP_ROLE, "flyway_schema_history", "DELETE")).isFalse();
    }

    @Test
    void theGroupCannotChangeTheSchema() {
        // USAGE without CREATE. Flyway owns every schema change and the
        // application runs with ddl-auto=none, so holding CREATE would be a
        // privilege nothing uses and something could misuse.
        Boolean usage = jdbc.queryForObject(
                "SELECT has_schema_privilege(?, 'public', 'USAGE')", Boolean.class, GROUP_ROLE);
        Boolean create = jdbc.queryForObject(
                "SELECT has_schema_privilege(?, 'public', 'CREATE')", Boolean.class, GROUP_ROLE);

        assertThat(usage).isTrue();
        assertThat(create).isFalse();
    }

    @Test
    void theAppendOnlyTriggerFromV7IsStillInPlace() {
        // Both halves, not one. The privileges are the control that survives the
        // trigger being removed; the trigger is the control that refuses the
        // superuser connection the tests and the migrations themselves run as.
        Integer triggers = jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_trigger
                WHERE tgrelid = 'activity_logs'::regclass
                  AND tgname = 'activity_logs_append_only'
                  AND NOT tgisinternal
                """,
                Integer.class);

        assertThat(triggers).isEqualTo(1);
    }

    @Test
    void aConnectionHoldingOnlyTheGroupIsRefusedAnAuditUpdate() throws SQLException {
        jdbc.execute("CREATE ROLE " + loginRole + " LOGIN PASSWORD 'probe-only-not-a-real-credential'");
        jdbc.execute("GRANT " + GROUP_ROLE + " TO " + loginRole);

        try (Connection connection = probeConnection();
                Statement statement = connection.createStatement()) {

            // Reading is allowed, which proves the connection works and is a member
            // of the group rather than being refused everything.
            statement.executeQuery("SELECT count(*) FROM activity_logs").close();

            // A no-op UPDATE on purpose. PostgreSQL checks the privilege before it
            // looks for rows, so this fails on the grant whether the table is empty
            // or not — and with no rows matched, V7's row-level trigger never runs.
            // That is what makes this test about the privilege and not about the
            // trigger: 42501 is insufficient_privilege, while the trigger raises
            // restrict_violation.
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE activity_logs SET action = action"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(thrown -> assertThat(((SQLException) thrown).getSQLState())
                            .isEqualTo("42501"));

            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM activity_logs"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(thrown -> assertThat(((SQLException) thrown).getSQLState())
                            .isEqualTo("42501"));

            // And the escape hatch the privilege exists to close: a role that cannot
            // UPDATE must also be unable to remove the trigger that would have
            // stopped it. This is refused for lacking ownership of the table.
            assertThatThrownBy(() ->
                            statement.executeUpdate("DROP TRIGGER activity_logs_append_only ON activity_logs"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private Connection probeConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), loginRole, "probe-only-not-a-real-credential");
    }

    private Boolean privilege(String role, String table, String action) {
        return jdbc.queryForObject(
                "SELECT has_table_privilege(?, ?, ?)", Boolean.class, role, table, action);
    }
}
