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
     * Every table the migrations so far are expected to create.
     *
     * <p>Listed rather than counted, so that a migration which renames a table or forgets one is a
     * failure here rather than a surprise later. A phase that adds tables adds them here in the same
     * commit, which is the point: the list is the record of what the schema is meant to contain.
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

    /** Added by {@code V4}, with the workspace settings and lifecycle columns. */
    private static final List<String> TEAM_TABLES = List.of("teams", "team_members");

    /** Added by {@code V5}. The label catalog is shared with tasks, which join it in {@code V6}. */
    private static final List<String> PROJECT_TABLES =
            List.of("projects", "project_members", "labels", "project_labels");

    /** Added by {@code V6}. The counter is what makes task numbering safe under concurrency. */
    private static final List<String> TASK_TABLES =
            List.of("tasks", "project_task_counters", "subtasks", "task_labels", "task_dependencies");

    /** Added by {@code V7}, together with the trigger that makes the audit table append only. */
    private static final List<String> COLLABORATION_TABLES =
            List.of("comments", "comment_mentions", "attachments", "activity_logs");

    @Test
    void theMigrationsCreateExactlyTheTablesTheyShould() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);

        // Both directions. Every expected table is present, and nothing else is:
        // a notification table appearing here would mean a later phase had been
        // merged early, or that Hibernate had created something behind Flyway's
        // back.
        assertThat(tables).containsExactlyInAnyOrderElementsOf(Stream.of(
                        Stream.of("flyway_schema_history"),
                        IDENTITY_TABLES.stream(),
                        TEAM_TABLES.stream(),
                        PROJECT_TABLES.stream(),
                        TASK_TABLES.stream(),
                        COLLABORATION_TABLES.stream())
                .flatMap(stream -> stream)
                .toList());
    }

    @Test
    void noLaterPhaseDomainTablesExistYet() {
        // Named explicitly because this is the invariant that will actually break
        // when the next phase lands, and it should break loudly and in one place.
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);

        assertThat(tables).doesNotContain("notifications");
    }
}
