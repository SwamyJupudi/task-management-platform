package com.company.taskmanagementplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * The trigram indexes from V11, and the only question worth asking about an expression index: does the
 * expression PostgreSQL recorded match the one the query actually produces?
 *
 * <p>Existence alone proves nothing. An index on {@code lower(title)} and a query on {@code
 * lower(title)} have to normalise to the same expression, and the failure mode when they do not is
 * silent — the index sits there, the query scans the table, and every test still passes. The riskiest of
 * the six is {@code users.email}, which is {@code citext}: PostgreSQL rewrites {@code lower(email)} to
 * {@code lower((email)::text)} through the implicit cast, and if the index and the query disagreed about
 * that cast the search would quietly never use it.
 *
 * <p><strong>Comparing expressions rather than reading plans, and that is deliberate.</strong> The
 * obvious test — plan the query and look for the index name — is unreliable on a table with a handful of
 * rows, and not for the reason one would expect. Disabling sequential scans does not help: PostgreSQL
 * simply reaches for one of the <em>other</em> partial indexes on the table, walks it, and applies the
 * {@code LIKE} as a filter, which is genuinely cheaper than a bitmap scan over almost no rows. So a plan
 * assertion would only start passing once the suite happened to have inserted enough data, which is a
 * test that fails for reasons unrelated to what it claims to check. What each assertion below does
 * instead is take the expression out of the plan's own predicate and the expression out of the index
 * definition and require them to be the same string — which is precisely the condition the planner
 * applies when it decides whether the index is a candidate at all.
 *
 * <p>{@link #theUserDirectorySearchReallyPlansOntoItsTrigramIndexes()} closes the loop with volume, in a
 * transaction that is rolled back, so there is one end-to-end proof that these are chosen and not merely
 * choosable.
 */
class SearchIndexIT extends AbstractIntegrationTest {

    /** The six indexes, each against the predicate the application really issues. */
    private static final Map<String, String> INDEXED_QUERIES = Map.of(
            "tasks_title_trgm_idx",
                    "SELECT id FROM tasks WHERE deleted_at IS NULL AND lower(title) LIKE '%auth%'",
            "projects_name_trgm_idx",
                    "SELECT id FROM projects WHERE deleted_at IS NULL AND lower(name) LIKE '%plat%'",
            "projects_key_trgm_idx",
                    "SELECT id FROM projects WHERE deleted_at IS NULL AND lower(key) LIKE '%plat%'",
            "users_email_trgm_idx",
                    "SELECT id FROM users WHERE deleted_at IS NULL AND lower(email) LIKE '%example%'",
            "users_first_name_trgm_idx",
                    "SELECT id FROM users WHERE deleted_at IS NULL AND lower(first_name) LIKE '%ada%'",
            "users_last_name_trgm_idx",
                    "SELECT id FROM users WHERE deleted_at IS NULL AND lower(last_name) LIKE '%smith%'");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theTrigramExtensionIsInstalled() {
        Integer installed = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);

        assertThat(installed).isEqualTo(1);
    }

    @Test
    void everyTrigramIndexExists() {
        List<String> found = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() "
                        + "AND indexname LIKE '%_trgm_idx'",
                String.class);

        assertThat(found).containsExactlyInAnyOrderElementsOf(INDEXED_QUERIES.keySet());
    }

    @Test
    void everyTrigramIndexIsAGinIndexOnALoweredExpressionAndPartialOnTheSoftDelete() {
        List<String> definitions = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() "
                        + "AND indexname LIKE '%_trgm_idx'",
                String.class);

        assertThat(definitions).hasSize(6).allSatisfy(definition -> assertThat(definition)
                .contains("USING gin")
                .contains("gin_trgm_ops")
                .contains("lower(")
                // Partial, matching the deleted_at predicate every one of these
                // searches carries unconditionally. A full index would work too but
                // would be larger; a partial one on a different predicate would be
                // silently unusable.
                .contains("WHERE (deleted_at IS NULL)"));
    }

    @Test
    void everyIndexExpressionMatchesTheExpressionItsQueryProduces() {
        // The assertion that actually protects against a silently unused index.
        INDEXED_QUERIES.forEach((indexName, sql) -> {
            String indexed = indexedExpression(indexName);
            String queried = predicateExpression(sql);

            assertThat(queried)
                    .as("the predicate of %n  %s%nmust be the expression indexed by %s", sql, indexName)
                    .isEqualTo(indexed);
        });
    }

    @Test
    void theCitextEmailColumnIsIndexedThroughTheSameCastTheQueryUses() {
        // Spelled out on its own because it is the one case where the two could
        // differ by something invisible in the source: email is citext, so both
        // sides have to agree on the cast to text that lower() forces.
        String indexed = indexedExpression("users_email_trgm_idx");

        assertThat(indexed).isEqualTo("lower((email)::text)");
        assertThat(predicateExpression(INDEXED_QUERIES.get("users_email_trgm_idx")))
                .isEqualTo(indexed);
    }

    @Test
    void theUserDirectorySearchReallyPlansOntoItsTrigramIndexes() {
        // The end-to-end proof, with enough rows that the trigram index is the
        // cheapest option rather than merely an eligible one. Everything happens in
        // a transaction that is rolled back, so the shared container is left exactly
        // as it was found and no other test sees these accounts.
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        INSERT INTO users
                            (email, password_hash, first_name, last_name, status, email_verified_at)
                        SELECT 'search-index-' || n || '@example.test',
                               'not-a-real-hash',
                               'Given' || n,
                               'Family' || n,
                               'ACTIVE',
                               now()
                        FROM generate_series(1, 20000) AS n
                        """);
                // Without fresh statistics the planner is still working from the
                // row count it had before the insert.
                statement.execute("ANALYZE users");

                return explain(statement, "SELECT id FROM users WHERE deleted_at IS NULL "
                        + "AND (lower(email) LIKE '%needle%' OR lower(first_name) LIKE '%needle%' "
                        + "OR lower(last_name) LIKE '%needle%')");
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
        });

        // All three, combined by a bitmap OR. Any one of them missing would send the
        // whole predicate back to a scan of the table.
        assertThat(plan).contains("users_email_trgm_idx");
        assertThat(plan).contains("users_first_name_trgm_idx");
        assertThat(plan).contains("users_last_name_trgm_idx");
        assertThat(plan).doesNotContain("Seq Scan on users");
    }

    /** The expression PostgreSQL stored for a one-column expression index, as it renders it. */
    private String indexedExpression(String indexName) {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_expr(i.indexprs, i.indrelid) FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indexrelid WHERE c.relname = ?",
                String.class,
                indexName);
        return normalise(definition);
    }

    /**
     * The {@code lower(...)} call on the left of the {@code LIKE}, as the planner renders it.
     *
     * <p>Taken from the plan rather than from the SQL text, which is the whole point: PostgreSQL rewrites
     * the expression — {@code lower(email)} on a {@code citext} column becomes {@code
     * lower((email)::text)} — and it is the rewritten form that has to match the index.
     */
    private String predicateExpression(String sql) {
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                return explain(statement, sql);
            }
        });

        // Rendered as "Filter: (lower(title) ~~ '%auth%'::text)", or as an Index
        // Cond in the same shape.
        int operator = plan.indexOf(" ~~ ");
        assertThat(operator).as("expected a LIKE predicate in the plan:%n%s", plan).isNotNegative();

        String left = plan.substring(0, operator);
        int start = left.lastIndexOf("lower(");
        assertThat(start).as("expected a lowered expression in the plan:%n%s", plan).isNotNegative();

        return normalise(left.substring(start, endOfCall(left, start)));
    }

    /** The index just past the closing parenthesis of the call starting at {@code from}. */
    private static int endOfCall(String text, int from) {
        int depth = 0;
        for (int i = from; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    private static String explain(Statement statement, String sql) throws java.sql.SQLException {
        List<String> lines = new ArrayList<>();
        try (ResultSet plan = statement.executeQuery("EXPLAIN " + sql)) {
            while (plan.next()) {
                lines.add(plan.getString(1));
            }
        }
        return String.join("\n", lines);
    }

    private static String normalise(String expression) {
        return expression == null ? null : expression.trim();
    }

}
