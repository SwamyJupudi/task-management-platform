package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

    @Test
    void foundationPhaseCreatesNoDomainTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);

        // Only Flyway's own bookkeeping table may exist at this point.
        assertThat(tables).containsExactly("flyway_schema_history");
    }
}
