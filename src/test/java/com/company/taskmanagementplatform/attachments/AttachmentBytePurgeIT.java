package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * The byte purge against a real database and the real local store: the bytes actually go, the row goes
 * with them, and nothing inside the retention window is touched.
 *
 * <p>Rows are backdated through JDBC rather than by waiting thirty days, and the retention is left at
 * the real thirty days so that the window is what is being tested. {@code AttachmentBytePurgeTest}
 * covers what happens when the store refuses, which cannot be provoked here without interfering with
 * the filesystem.
 */
class AttachmentBytePurgeIT extends AbstractCollaborationIT {

    @Autowired
    private AttachmentBytePurge purge;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StorageProperties storage;

    @Autowired
    private AttachmentPurgeProperties purgeProperties;

    @Test
    void reclaimsTheBytesAndTheRowOfAFileDeletedBeyondTheRetention() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "old.pdf", CollaborationFixtures.PDF, scene.adminId());
        Path stored = storedPath(file.id());
        assertThat(stored).exists();

        softDelete(file.id(), daysAgo(40));
        purge.run();

        assertThat(stored).doesNotExist();
        assertThat(rowExists(file.id())).isFalse();
    }

    @Test
    void leavesAFileDeletedInsideTheRetentionAlone() {
        // This is the window in which a file deleted by mistake can still be
        // recovered. Nothing else protects it.
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "recent.pdf", CollaborationFixtures.PDF, scene.adminId());
        Path stored = storedPath(file.id());

        softDelete(file.id(), daysAgo(3));
        purge.run();

        assertThat(stored).exists();
        assertThat(rowExists(file.id())).isTrue();
    }

    @Test
    void neverTouchesALiveFile() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "live.pdf", CollaborationFixtures.PDF, scene.adminId());
        Path stored = storedPath(file.id());

        purge.run();

        assertThat(stored).exists();
        assertThat(rowExists(file.id())).isTrue();
    }

    @Test
    void clearsABacklogLargerThanOneBatch() {
        // The batch size is two in the test profile, so three files means the loop
        // has to run more than once.
        Scene scene = scene();
        AttachmentResponse first =
                collaboration.attachment(ref(scene), "a.pdf", CollaborationFixtures.PDF, scene.adminId());
        AttachmentResponse second =
                collaboration.attachment(ref(scene), "b.pdf", CollaborationFixtures.PDF, scene.adminId());
        AttachmentResponse third =
                collaboration.attachment(ref(scene), "c.pdf", CollaborationFixtures.PDF, scene.adminId());

        softDelete(first.id(), daysAgo(40));
        softDelete(second.id(), daysAgo(41));
        softDelete(third.id(), daysAgo(42));

        assertThat(purgeProperties.batchSize()).isEqualTo(2);
        purge.run();

        assertThat(rowExists(first.id())).isFalse();
        assertThat(rowExists(second.id())).isFalse();
        assertThat(rowExists(third.id())).isFalse();
    }

    @Test
    void isIdempotentSoASecondRunFindsNothingLeftToDo() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "once.pdf", CollaborationFixtures.PDF, scene.adminId());
        softDelete(file.id(), daysAgo(40));

        purge.run();
        AttachmentBytePurge.Result second = purge.run();

        assertThat(second.failed()).isZero();
        assertThat(rowExists(file.id())).isFalse();
    }

    @Test
    void reclaimsARowWhoseObjectHasAlreadyGoneMissing() {
        // The self-healing half of the ordering rule. A run that removed the object
        // and then died before the row leaves exactly this state, and the next run
        // has to finish the job rather than fail on it for ever.
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "orphan.pdf", CollaborationFixtures.PDF, scene.adminId());
        Path stored = storedPath(file.id());

        softDelete(file.id(), daysAgo(40));
        deleteQuietly(stored);
        assertThat(stored).doesNotExist();

        AttachmentBytePurge.Result result = purge.run();

        assertThat(result.failed()).isZero();
        assertThat(rowExists(file.id())).isFalse();
    }

    @Test
    void theScheduledEntryPointDoesNothingWhileTheJobIsSwitchedOff() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "safe.pdf", CollaborationFixtures.PDF, scene.adminId());
        softDelete(file.id(), daysAgo(40));

        assertThat(purgeProperties.enabled()).isFalse();
        purge.scheduled();

        assertThat(rowExists(file.id())).isTrue();
        assertThat(storedPath(file.id())).exists();
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    /** Marks the row deleted at a chosen moment, which is how the window is reached without waiting. */
    private void softDelete(UUID attachmentId, Instant deletedAt) {
        jdbc.update(
                "UPDATE attachments SET deleted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(deletedAt),
                attachmentId);
    }

    private Path storedPath(UUID attachmentId) {
        String key = jdbc.queryForObject(
                "SELECT storage_key FROM attachments WHERE id = ?", String.class, attachmentId);
        return Path.of(storage.localDirectory()).toAbsolutePath().normalize().resolve(key);
    }

    private boolean rowExists(UUID attachmentId) {
        Integer found = jdbc.queryForObject(
                "SELECT count(*) FROM attachments WHERE id = ?", Integer.class, attachmentId);
        return found != null && found == 1;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
