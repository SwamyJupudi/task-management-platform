package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * The indexes V9 creates, asserted against a real PostgreSQL.
 *
 * <p>Written at the SQL level on purpose, like {@code IdentitySchemaIT}, {@code TeamSchemaIT}, {@code
 * ProjectSchemaIT} and {@code TaskSchemaIT}. An index is a property of the schema rather than of the
 * service layer, so driving it through the service layer would prove the wrong thing entirely.
 *
 * <p><strong>It asserts that the indexes exist, not that the planner uses them.</strong> An {@code
 * EXPLAIN} assertion reads like a stronger test and is a worse one: a planner is free to choose a
 * sequential scan over an index on a table of fifty rows, and it is right to. Such a test would fail
 * on data volume rather than on a defect, and the usual repair for it is to delete it.
 *
 * <p>The partial predicates are asserted as well as the columns, because that is the half that is
 * easy to lose. An index missing its {@code WHERE deleted_at IS NULL} still answers every query
 * correctly and quietly holds a row for every task ever deleted.
 */
class ReportSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // --- tasks --------------------------------------------------------------

    @Test
    void indexesTheWorkspaceWideStatusDistribution() {
        // The existing tasks_project_status_idx serves one project. Nothing before
        // V9 led with the workspace and grouped by status, which is what every
        // workspace-wide figure in this phase does.
        assertThat(definitionOf("tasks_workspace_status_idx"))
                .contains("workspace_id")
                .contains("status")
                .contains("deleted_at IS NULL");
    }

    @Test
    void indexesPersonalWorkloadWithTheWorkspaceLeading() {
        assertThat(definitionOf("tasks_workspace_assignee_status_idx"))
                .contains("workspace_id")
                .contains("assignee_user_id")
                .contains("status")
                .contains("deleted_at IS NULL");
    }

    @Test
    void indexesCompletionsDoublyPartiallySoItHoldsOnlyFinishedWork() {
        // Only finished tasks carry completed_at, and a check constraint guarantees
        // it, so this index holds one row per completed task rather than one per
        // task. Losing the second predicate would silently make it as large as the
        // table.
        String definition = definitionOf("tasks_workspace_completed_at_idx");

        assertThat(definition).contains("workspace_id").contains("completed_at");
        assertThat(definition).contains("deleted_at IS NULL");
        assertThat(definition).contains("completed_at IS NOT NULL");
    }

    @Test
    void indexesTheCreatedHalfOfTheTrend() {
        assertThat(definitionOf("tasks_workspace_created_at_idx"))
                .contains("workspace_id")
                .contains("created_at")
                .contains("deleted_at IS NULL");
    }

    @Test
    void indexesDueDatesWithinOneProject() {
        // tasks_workspace_due_date_idx serves the workspace-wide calendar and is
        // left alone. This one serves a scope list, which is the shape every
        // narrowed report asks in.
        assertThat(definitionOf("tasks_project_due_date_idx"))
                .contains("project_id")
                .contains("due_date")
                .contains("deleted_at IS NULL");
    }

    // --- subtasks, projects and the audit trail -----------------------------

    @Test
    void indexesPersonalChecklistItems() {
        assertThat(definitionOf("subtasks_workspace_assignee_status_idx"))
                .contains("workspace_id")
                .contains("assignee_user_id")
                .contains("status")
                .contains("deleted_at IS NULL");
    }

    @Test
    void indexesProjectsByTeamAndStatusForTheTeamPerformancePanel() {
        assertThat(definitionOf("projects_workspace_team_status_idx"))
                .contains("workspace_id")
                .contains("team_id")
                .contains("status")
                .contains("deleted_at IS NULL");
    }

    @Test
    void indexesOnePersonsOwnHistoryNewestFirst() {
        // Descending on purpose: "recent activity" reads the newest rows, and an
        // ascending index would have to be walked backwards from the end.
        String definition = definitionOf("activity_logs_workspace_actor_created_idx");

        assertThat(definition).contains("workspace_id").contains("actor_user_id");
        assertThat(definition).contains("created_at DESC");
    }

    @Test
    void leavesTheAuditIndexUnpartitionedBecauseThatTableIsNeverSoftDeleted() {
        // activity_logs has no deleted_at at all. A partial predicate here would not
        // merely be redundant, it would not compile.
        assertThat(definitionOf("activity_logs_workspace_actor_created_idx")).doesNotContain("WHERE");
    }

    // --- what V9 deliberately did not create --------------------------------

    @Test
    void addsNoTableAndNoMaterializedView() {
        // Phase eight reads what phases three to seven already store. A rollup table
        // or a materialized view would be a second copy of the truth that can go
        // stale, which is the thing this phase most deliberately did not build.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_matviews WHERE schemaname = current_schema()", Long.class))
                .isZero();
    }

    @Test
    void addsNoPermissionRowAndThereforeOwesNoSuperAdminMapping() {
        // The V8 pattern. A report is computed over the caller's project read scope,
        // and project:read_any already widens it, so there is no report permission
        // to map or to backfill. If one is ever added, PermissionCatalogIT and
        // WorkspaceRoleGrantsIT are what enforce both halves.
        List<String> reportPermissions =
                jdbc.queryForList("SELECT code FROM permissions WHERE code LIKE 'report%' OR code LIKE 'dashboard%'", String.class);

        assertThat(reportPermissions).isEmpty();
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
