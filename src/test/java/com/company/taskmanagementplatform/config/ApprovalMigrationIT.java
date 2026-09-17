package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * That {@code V14__approval_onboarding.sql} survives contact with an installation that has people in
 * it.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>V14 shipped broken and every other test passed. It rewrote {@code PENDING_VERIFICATION} rows to
 * {@code PENDING_APPROVAL} <em>before</em> dropping the check constraint that permitted only the old
 * three values, so PostgreSQL refused the update with {@code 23514}. Nothing caught it because every
 * other migration test runs against a database Flyway has just created: there are no rows to rewrite,
 * the {@code UPDATE} touches nothing, and the constraint is never exercised. The only installation
 * that fails is one with real accounts in it.
 *
 * <p>So this does the one thing the rest of the suite cannot: it builds the schema as it stood
 * <strong>before</strong> V14, puts a row in it that only an older installation would have, and then
 * runs V14 over the top.
 *
 * <h2>A separate database, not a separate schema</h2>
 *
 * <p>The first attempt used a second schema in the shared database and broke three unrelated test
 * classes. The reason is worth recording, because it is not obvious:
 *
 * <ul>
 *   <li>Catalogue queries stop being unique. {@code EnumConstraintIT} reads {@code pg_constraint
 *       WHERE conname = ?} and expects one row; a second schema holding a second copy of every
 *       object returns two.
 *   <li>Worse, {@code V13} is written against {@code public} by name. Its blanket {@code GRANT ... ON
 *       ALL TABLES IN SCHEMA public} re-granted {@code UPDATE} and {@code DELETE} on the real {@code
 *       activity_logs}, while the {@code REVOKE} that takes them back is unqualified and resolved
 *       through the search path to the copy in the test's schema. The audit trail in the suite's own
 *       database quietly became writable, which is what {@code AuditRoleSeparationIT} then reported.
 * </ul>
 *
 * <p>A separate database has neither problem. Extensions, schemas, tables and grants are all
 * per-database, so {@code public} means this database's {@code public} and nothing here can reach the
 * one every other test uses. Roles are the one thing that stays cluster-wide, and V13 only creates
 * that role when it is missing.
 */
class ApprovalMigrationIT extends AbstractIntegrationTest {

    /** Never the database the rest of the suite runs in. Asserted before anything destructive. */
    private static final String DATABASE = "v14_regression";

    private JdbcTemplate jdbc;

    @BeforeEach
    void rewindToThePreviousSchema() {
        assertThat(DATABASE).isNotEqualTo(POSTGRES.getDatabaseName());

        recreateDatabase();
        jdbc = new JdbcTemplate(regressionDataSource());

        // The schema as it stood before this migration existed.
        flyway("13").migrate();
    }

    /**
     * Takes the database away again.
     *
     * <p>It holds a whole copy of the schema and a Flyway history of its own; leaving it behind would
     * make the container's state depend on whether this class has run yet.
     */
    @AfterAll
    static void removeTheRegressionDatabase() {
        adminTemplate().execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    }

    @Test
    void v14MovesAnExistingUnverifiedAccountIntoTheApprovalQueue() {
        // The row an older installation has and a freshly created database does not.
        // Its status is the one V14 rewrites, and the constraint in force at that
        // moment is the one that refused it in production.
        jdbc.update(
                """
                INSERT INTO users (email, password_hash, first_name, last_name, status)
                VALUES ('waiting@example.com', 'hash', 'Ada', 'Lovelace', 'PENDING_VERIFICATION')
                """);

        assertThatCode(() -> flyway("14").migrate())
                .as("V14 must not be refused by the constraint it is replacing")
                .doesNotThrowAnyException();

        assertThat(status("waiting@example.com")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void v14LeavesEveryOtherStatusAlone() {
        jdbc.update(
                """
                INSERT INTO users (email, password_hash, first_name, last_name, status, email_verified_at)
                VALUES ('active@example.com', 'hash', 'Grace', 'Hopper', 'ACTIVE', now()),
                       ('off@example.com', 'hash', 'Alan', 'Turing', 'DEACTIVATED', now())
                """);

        flyway("14").migrate();

        assertThat(status("active@example.com")).isEqualTo("ACTIVE");
        assertThat(status("off@example.com")).isEqualTo("DEACTIVATED");
    }

    @Test
    void theNewConstraintAcceptsExactlyTheThreeStatusesThatRemain() {
        flyway("14").migrate();

        for (String accepted : new String[] {"PENDING_APPROVAL", "ACTIVE", "DEACTIVATED"}) {
            // ACTIVE is accepted without a confirmed address, which is the other
            // constraint V14 drops.
            assertThatCode(() -> insert("ok-" + accepted + "@example.com", accepted))
                    .as("status %s must be accepted", accepted)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> insert("gone@example.com", "PENDING_VERIFICATION"))
                .as("the status V14 retired must no longer be writable")
                .hasMessageContaining("users_status_check");
    }

    @Test
    void v14RemovesTheInvitationsTableFromAnInstallationThatHadOne() {
        // Present before, gone after: the destructive half of the migration running
        // against something rather than nothing.
        assertThat(tableExists("workspace_invitations")).isTrue();

        flyway("14").migrate();

        assertThat(tableExists("workspace_invitations")).isFalse();
    }

    // --- the isolated database -------------------------------------------------

    private void insert(String email, String status) {
        jdbc.update(
                "INSERT INTO users (email, password_hash, first_name, last_name, status) VALUES (?, 'h', 'A', 'B', ?)",
                email,
                status);
    }

    private String status(String email) {
        return jdbc.queryForObject("SELECT status FROM users WHERE email = ?", String.class, email);
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class,
                table);
        return count != null && count > 0;
    }

    /** Dropped and recreated per test, so one test's rows cannot decide another's outcome. */
    private static void recreateDatabase() {
        JdbcTemplate admin = adminTemplate();
        admin.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
        admin.execute("CREATE DATABASE " + DATABASE);
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(regressionDataSource())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    /** Connected to the container's own database, which exists here only to create and drop another. */
    private static JdbcTemplate adminTemplate() {
        return new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static DataSource regressionDataSource() {
        // Same host and port, different database. Everything the migrations touch --
        // extensions, schemas, tables, grants -- is scoped to it.
        String url = POSTGRES.getJdbcUrl()
                .replaceFirst("/" + POSTGRES.getDatabaseName() + "(\\?|$)", "/" + DATABASE + "$1");
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
