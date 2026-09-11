package com.company.taskmanagementplatform.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * The guarantees this phase asks the database for, exercised as SQL.
 *
 * <p>The other tests drive the application and would pass against a schema that merely happened to
 * be well behaved. These write statements the application would never write, because a constraint
 * that is only ever satisfied is a constraint nobody has checked.
 *
 * <p>The append-only rule is the reason this class matters most. The requirements say audit records
 * must not be casually editable or deletable, and every test above writes them through a service
 * that has no method for either. This is the test that tries anyway.
 */
class CollaborationSchemaIT extends AbstractCollaborationIT {

    @Autowired
    private JdbcTemplate jdbc;

    // --- the audit trail is append only ----------------------------------

    @Test
    void anAuditRowCannotBeUpdated() {
        Scene scene = scene();
        collaboration.comment(ref(scene), "something happened", scene.adminId());
        UUID entryId = anyEntry(scene.workspaceId());

        assertThatThrownBy(() -> jdbc.update("UPDATE activity_logs SET action = 'forged' WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append only");
    }

    @Test
    void anAuditRowCannotBeDeleted() {
        Scene scene = scene();
        collaboration.comment(ref(scene), "something happened", scene.adminId());
        UUID entryId = anyEntry(scene.workspaceId());

        assertThatThrownBy(() -> jdbc.update("DELETE FROM activity_logs WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append only");
    }

    @Test
    void theWholeTableCannotBeEmptiedEither() {
        Scene scene = scene();
        collaboration.comment(ref(scene), "something happened", scene.adminId());

        anyEntry(scene.workspaceId());

        assertThatThrownBy(() -> jdbc.update("DELETE FROM activity_logs WHERE workspace_id = ?", scene.workspaceId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theAuditTableCarriesNoUpdatedOrDeletedColumn() {
        // Their absence is the design. A soft-deletable audit row is not an audit row.
        assertThat(columnsOf("activity_logs")).doesNotContain("updated_at", "deleted_at");
    }

    @Test
    void anUnknownEntityKindIsRefused() {
        Scene scene = scene();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO activity_logs (workspace_id, action, entity_type, entity_id)
                        VALUES (?, 'thing.happened', 'GALAXY', ?)
                        """,
                        scene.workspaceId(),
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    // --- comments ---------------------------------------------------------

    @Test
    void aCommentCannotBeEmptyOrOverlyLong() {
        Scene scene = scene();

        assertThatThrownBy(() -> insertComment(scene, "   ")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertComment(scene, "x".repeat(5001))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void aCommentCannotClaimATaskFromAnotherProject() {
        // The composite key, not a service check.
        Scene scene = scene();
        Scene other = scene();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO comments (workspace_id, project_id, task_id, author_user_id, body)
                        VALUES (?, ?, ?, ?, 'smuggled')
                        """,
                        scene.workspaceId(),
                        scene.projectId(),
                        other.taskId(),
                        scene.adminId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void namingTheSamePersonTwiceInOneCommentIsOneRow() {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");
        CommentResponse comment = collaboration.comment(
                ref(scene), "hello " + CollaborationFixtures.mention(colleague), scene.adminId());

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO comment_mentions (comment_id, mentioned_user_id, workspace_id)
                        VALUES (?, ?, ?)
                        """,
                        comment.id(),
                        colleague,
                        scene.workspaceId()))
                .isInstanceOf(DataAccessException.class);
    }

    // --- attachments ------------------------------------------------------

    @Test
    void twoFilesCannotShareAStorageKey() {
        // Not partial on deleted_at, deliberately: a soft-deleted attachment still
        // owns its object until the purge, and reusing its key would overwrite
        // bytes somebody may yet restore.
        Scene scene = scene();
        collaboration.attachment(ref(scene), "one.pdf", CollaborationFixtures.PDF, scene.adminId());
        String key = jdbc.queryForObject(
                "SELECT storage_key FROM attachments WHERE task_id = ? LIMIT 1", String.class, scene.taskId());

        assertThatThrownBy(() -> insertAttachment(scene, key)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void aFileCannotClaimACommentOnADifferentTask() {
        Scene scene = scene();
        Scene other = scene();
        CommentResponse elsewhere = collaboration.comment(ref(other), "over there", other.adminId());

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO attachments (workspace_id, project_id, task_id, comment_id, uploader_user_id,
                                                 filename, content_type, size_bytes, checksum_sha256,
                                                 storage_provider, storage_key)
                        VALUES (?, ?, ?, ?, ?, 'x.pdf', 'application/pdf', 10, ?, 'LOCAL', ?)
                        """,
                        scene.workspaceId(),
                        scene.projectId(),
                        scene.taskId(),
                        elsewhere.id(),
                        scene.adminId(),
                        "0".repeat(64),
                        UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aFileMustHaveAPlausibleChecksumAndSize() {
        Scene scene = scene();

        assertThatThrownBy(() -> insertAttachmentWith(scene, "not-a-checksum", 10))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertAttachmentWith(scene, "0".repeat(64), 0))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void anUnknownStorageProviderIsRefused() {
        Scene scene = scene();

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO attachments (workspace_id, project_id, task_id, uploader_user_id,
                                                 filename, content_type, size_bytes, checksum_sha256,
                                                 storage_provider, storage_key)
                        VALUES (?, ?, ?, ?, 'x.pdf', 'application/pdf', 10, ?, 'DROPBOX', ?)
                        """,
                        scene.workspaceId(),
                        scene.projectId(),
                        scene.taskId(),
                        scene.adminId(),
                        "0".repeat(64),
                        UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void everyNewTableIsSoftDeletableExceptTheJoinAndTheAudit() {
        // The convention: soft deletion where restoring matters, never on a join
        // table, tokens, notifications or audit rows.
        assertThat(columnsOf("comments")).contains("deleted_at");
        assertThat(columnsOf("attachments")).contains("deleted_at");
        assertThat(columnsOf("comment_mentions")).doesNotContain("deleted_at");
        assertThat(columnsOf("activity_logs")).doesNotContain("deleted_at");
    }

    /**
     * One written audit row, waiting for it if the trail has not caught up.
     *
     * <p>Rows are written on the activity module's own thread a moment after the commit, so a test
     * that read immediately would be reading a race rather than a schema.
     */
    private UUID anyEntry(UUID workspaceId) {
        UUID[] found = new UUID[1];
        eventually(() -> found[0] = jdbc.queryForObject(
                "SELECT id FROM activity_logs WHERE workspace_id = ? LIMIT 1", UUID.class, workspaceId));
        return found[0];
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class, table);
    }

    private void insertComment(Scene scene, String body) {
        jdbc.update(
                """
                INSERT INTO comments (workspace_id, project_id, task_id, author_user_id, body)
                VALUES (?, ?, ?, ?, ?)
                """,
                scene.workspaceId(),
                scene.projectId(),
                scene.taskId(),
                scene.adminId(),
                body);
    }

    private void insertAttachment(Scene scene, String storageKey) {
        insertAttachmentWith(scene, "0".repeat(64), 10, storageKey);
    }

    private void insertAttachmentWith(Scene scene, String checksum, long size) {
        insertAttachmentWith(scene, checksum, size, UUID.randomUUID().toString());
    }

    private void insertAttachmentWith(Scene scene, String checksum, long size, String storageKey) {
        jdbc.update(
                """
                INSERT INTO attachments (workspace_id, project_id, task_id, uploader_user_id,
                                         filename, content_type, size_bytes, checksum_sha256,
                                         storage_provider, storage_key)
                VALUES (?, ?, ?, ?, 'x.pdf', 'application/pdf', ?, ?, 'LOCAL', ?)
                """,
                scene.workspaceId(),
                scene.projectId(),
                scene.taskId(),
                scene.adminId(),
                size,
                checksum,
                storageKey);
    }
}
