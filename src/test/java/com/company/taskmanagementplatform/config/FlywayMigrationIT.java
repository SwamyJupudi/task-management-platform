package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * Flyway is the only source of schema truth, so these tests assert both halves of that claim: the
 * migrations ran, and Hibernate did not create anything behind their back.
 */
class FlywayMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsAreAppliedAndSuccessful() {
        Integer failed =
                jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        Integer applied = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isPositive();
    }

    @Test
    void requiredExtensionsAreInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(extensions).contains("pgcrypto", "citext");
    }

    /**
     * Every table the identity phase is expected to create.
     *
     * <p>Listed rather than counted, so that a migration which renames a table or forgets one is a
     * failure here rather than a surprise later.
     */
    private static final List<String> IDENTITY_TABLES = List.of(
            "permissions",
            "refresh_tokens",
            "role_permissions",
            "roles",
            "user_tokens",
            "users",
            "workspace_invitations",
            "workspace_members",
            "workspaces");

    @Test
    void theIdentityPhaseCreatesExactlyItsOwnTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);

        // Both directions. Every identity table is present, and nothing else is:
        // a project or task table appearing here would mean a later phase had been
        // merged early, or that Hibernate had created something behind Flyway's back.
        assertThat(tables).containsExactlyInAnyOrderElementsOf(
                Stream.concat(Stream.of("flyway_schema_history"), IDENTITY_TABLES.stream())
                        .toList());
    }

    @Test
    void noLaterPhaseDomainTablesExistYet() {
        // Named explicitly because this is the invariant that will actually break
        // when the next phase lands, and it should break loudly and in one place.
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);

        assertThat(tables).doesNotContain("projects", "tasks", "task_comments", "attachments", "task_labels");
    }
}
