package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * The backlog {@code V12} creates, and the only thing that clears it.
 *
 * <p>{@code V12} records every attachment that predates malware scanning as {@code PENDING} rather than
 * backfilling it to {@code CLEAN}, because nothing had inspected those files. These tests are the other half
 * of that decision: they prove such a row is not treated as scanned, that it cannot be downloaded, that a
 * successful scan is what makes it downloadable, and that a file the scanner refuses or cannot reach stays
 * blocked.
 *
 * <p><strong>How a pre-migration row is simulated.</strong> The migration has already run against the shared
 * container by the time any test starts, so there is no way to create a row that literally predates it. What
 * such a row <em>is</em>, though, is precisely defined: {@code scan_status = 'PENDING'} with a null {@code
 * scanned_at}, which is what the column default produces for a row inserted without a scan. Each test below
 * puts a row into exactly that state and then asserts against it, and {@link
 * #theColumnDefaultIsPendingSoARowWrittenWithoutAScanIsNotTreatedAsScanned()} pins the default itself so the
 * simulation cannot drift away from what the migration does.
 */
@Import(AttachmentRescanIT.ScannerOverride.class)
class AttachmentRescanIT extends AbstractCollaborationIT {

    /** A deterministic scanner, so a test can ask for a clean, an infected or an unreachable verdict. */
    @TestConfiguration
    static class ScannerOverride {

        @Bean
        @Primary
        FakeMalwareScanner fakeMalwareScanner() {
            return new FakeMalwareScanner();
        }
    }

    @Autowired
    private AttachmentRescan rescan;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StorageProperties storage;

    @Autowired
    private AttachmentRescanProperties rescanProperties;

    /**
     * Takes every other test's leftover unscanned rows out of this job's view.
     *
     * <p>The rescan is estate-wide by nature: it looks for unscanned attachments, not for this test's unscanned
     * attachments. The container is shared across the suite, and {@code MalwareScanLifecycleIT} deliberately
     * leaves rows in {@code PENDING}, {@code SCANNING} and {@code REJECTED}, so without this a run started here
     * would also work through those and the counters it returns would depend on which classes had run first.
     * Worse, two leftover files the scanner cannot resolve would trip this job's "a whole batch failed" stop and
     * it would never reach the row a test had just set up.
     *
     * <p>Soft-deleting them is the honest way to do it: {@code findUnscanned} excludes deleted rows, so they
     * leave the job's view without anything claiming they were scanned. Marking them clean would have been the
     * other way to clear the view, and it is exactly the lie this whole feature exists to refuse.
     */
    @BeforeEach
    void takeOtherTestsBacklogOutOfView() {
        jdbc.update("UPDATE attachments SET deleted_at = now() "
                + "WHERE deleted_at IS NULL AND scan_status <> 'CLEAN'");
    }

    // --- existing attachments are not treated as scanned ---------------------

    @Test
    void theColumnDefaultIsPendingSoARowWrittenWithoutAScanIsNotTreatedAsScanned() {
        // The migration adds the column with this default and deliberately does not
        // backfill, so every row that already existed ends up here. If this default
        // ever became CLEAN, an unscanned backlog would silently start being served.
        String columnDefault = jdbc.queryForObject(
                """
                SELECT column_default FROM information_schema.columns
                WHERE table_name = 'attachments' AND column_name = 'scan_status'
                """,
                String.class);

        assertThat(columnDefault).contains("PENDING");
    }

    @Test
    void theMigrationLeavesNoAttachmentMarkedCleanWithoutAScanTimestamp() {
        // A CLEAN row with no scanned_at would be a file claimed as inspected that
        // nothing inspected — exactly what backfilling would have produced. The
        // schema refuses it, and this asserts the data agrees.
        Integer cleanButNeverScanned = jdbc.queryForObject(
                "SELECT count(*) FROM attachments WHERE scan_status = 'CLEAN' AND scanned_at IS NULL",
                Integer.class);

        assertThat(cleanButNeverScanned).isZero();
    }

    @Test
    void anExistingAttachmentIsPendingAndCarriesNoScanTimestamp() {
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.pdf", CollaborationFixtures.PDF);

        assertThat(scanStatusOf(file.id())).isEqualTo("PENDING");
        assertThat(scannedAtOf(file.id())).isNull();
    }

    @Test
    void theDatabaseRefusesAnAttemptToMarkTheBacklogCleanWithoutScanningIt() {
        // The shortcut somebody will reach for when the backlog is inconvenient. The
        // check constraint is what makes "only a successful scan produces CLEAN" true
        // of the data rather than only of the code paths.
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.pdf", CollaborationFixtures.PDF);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                        "UPDATE attachments SET scan_status = 'CLEAN' WHERE id = ?", file.id())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(scanStatusOf(file.id())).isEqualTo("PENDING");
    }

    // --- unscanned attachments cannot be downloaded --------------------------

    @Test
    void anUnscannedAttachmentCannotBeDownloaded() {
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.pdf", CollaborationFixtures.PDF);

        assertThatDownloadIsRefused(scene, file.id());
    }

    @Test
    void anUnscannedAttachmentIsStillListedAndStillHasReadableMetadata() throws Exception {
        // The guard is on the bytes, not on the record. Upgrading an installation must
        // not make files vanish from a task; it makes them temporarily unavailable,
        // which is a thing somebody can understand and wait out.
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.pdf", CollaborationFixtures.PDF);

        mockMvc.perform(get(metadataPath(scene, file.id())).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(file.id().toString()));

        mockMvc.perform(get(listPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(file.id().toString()));
    }

    // --- a successful scan makes them CLEAN ---------------------------------

    @Test
    void aSuccessfulScanPromotesAnExistingAttachmentToCleanAndItDownloads() throws Exception {
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.pdf", CollaborationFixtures.PDF);
        assertThatDownloadIsRefused(scene, file.id());

        AttachmentRescan.Result result = rescan.run();

        assertThat(result.cleared()).isGreaterThanOrEqualTo(1);
        assertThat(scanStatusOf(file.id())).isEqualTo("CLEAN");
        // Stamped, because CLEAN is a claim that something looked.
        assertThat(scannedAtOf(file.id())).isNotNull();

        mockMvc.perform(get(contentPath(scene, file.id())).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk());
    }

    @Test
    void theScanReadsTheFilesOwnBytesRatherThanTrustingItsAge() {
        // The point of the rescan. A file that predates scanning is not clean because
        // it is old; it is clean because a scanner has now looked at what is actually
        // in the store. Here the stored bytes are infected and the row is otherwise
        // indistinguishable from any other piece of backlog.
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.txt", "ordinary text".getBytes(StandardCharsets.UTF_8));
        overwriteStoredBytes(file.id(), infectedContent());

        rescan.run();

        assertThat(scanStatusOf(file.id())).isEqualTo("REJECTED");
    }

    @Test
    void clearsABacklogLargerThanOneBatch() {
        // Two per batch in the test profile, so three files means the loop runs more
        // than once. Each promotion removes a row from the result set, which is why the
        // job re-reads the first page rather than paging forward.
        Scene scene = scene();
        AttachmentResponse first = existingUnscannedAttachment(scene, "a.pdf", CollaborationFixtures.PDF);
        AttachmentResponse second = existingUnscannedAttachment(scene, "b.pdf", CollaborationFixtures.PDF);
        AttachmentResponse third = existingUnscannedAttachment(scene, "c.pdf", CollaborationFixtures.PDF);

        assertThat(rescanProperties.batchSize()).isEqualTo(2);
        rescan.run();

        assertThat(scanStatusOf(first.id())).isEqualTo("CLEAN");
        assertThat(scanStatusOf(second.id())).isEqualTo("CLEAN");
        assertThat(scanStatusOf(third.id())).isEqualTo("CLEAN");
    }

    @Test
    void leavesAnAlreadyCleanAttachmentAloneRatherThanScanningItAgain() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "fresh.pdf", CollaborationFixtures.PDF, scene.adminId());
        java.sql.Timestamp scannedAtUpload = scannedAtOf(file.id());

        rescan.run();

        assertThat(scanStatusOf(file.id())).isEqualTo("CLEAN");
        assertThat(scannedAtOf(file.id())).isEqualTo(scannedAtUpload);
    }

    // --- infected and unscannable attachments remain blocked -----------------

    @Test
    void anInfectedExistingAttachmentIsRejectedAndStaysBlocked() {
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.txt", "ordinary text".getBytes(StandardCharsets.UTF_8));
        overwriteStoredBytes(file.id(), infectedContent());

        AttachmentRescan.Result result = rescan.run();

        assertThat(result.rejected()).isGreaterThanOrEqualTo(1);
        assertThat(scanStatusOf(file.id())).isEqualTo("REJECTED");
        assertThat(signatureOf(file.id())).isEqualTo(FakeMalwareScanner.SIGNATURE);
        assertThatDownloadIsRefused(scene, file.id());
    }

    @Test
    void aRejectedAttachmentIsNotPromotedByALaterRun() {
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.txt", "ordinary text".getBytes(StandardCharsets.UTF_8));
        overwriteStoredBytes(file.id(), infectedContent());
        rescan.run();

        // Even if the bytes are replaced with something harmless afterwards, a verdict
        // already recorded is not revisited by this job.
        overwriteStoredBytes(file.id(), "now harmless".getBytes(StandardCharsets.UTF_8));
        rescan.run();

        assertThat(scanStatusOf(file.id())).isEqualTo("REJECTED");
        assertThatDownloadIsRefused(scene, file.id());
    }

    @Test
    void anAttachmentTheScannerCannotLookAtStaysPendingAndStaysBlocked() {
        // The fail-closed direction. An unreachable scanner must never be the reason a
        // file becomes servable.
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.txt", "ordinary text".getBytes(StandardCharsets.UTF_8));
        overwriteStoredBytes(file.id(), unscannableContent());

        AttachmentRescan.Result result = rescan.run();

        assertThat(result.unresolved()).isGreaterThanOrEqualTo(1);
        assertThat(scanStatusOf(file.id())).isEqualTo("PENDING");
        assertThat(scannedAtOf(file.id())).isNull();
        assertThatDownloadIsRefused(scene, file.id());
    }

    @Test
    void anAttachmentWhoseBytesHaveGoneMissingStaysPendingRatherThanBeingCleared() {
        // Nothing to scan is not the same as nothing found. A missing object must not
        // promote a file.
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.pdf", CollaborationFixtures.PDF);
        deleteStoredBytes(file.id());

        rescan.run();

        assertThat(scanStatusOf(file.id())).isEqualTo("PENDING");
        assertThatDownloadIsRefused(scene, file.id());
    }

    @Test
    void theScheduledEntryPointDoesNothingWhileTheJobIsSwitchedOff() {
        Scene scene = scene();
        AttachmentResponse file = existingUnscannedAttachment(scene, "legacy.pdf", CollaborationFixtures.PDF);

        assertThat(rescanProperties.enabled()).isFalse();
        rescan.scheduled();

        assertThat(scanStatusOf(file.id())).isEqualTo("PENDING");
    }

    // --- helpers -------------------------------------------------------------

    /**
     * An attachment in the state {@code V12} leaves every pre-existing row in.
     *
     * <p>Uploaded normally and then reset to the column default, because the migration has already run against
     * the shared container and a row that literally predates it cannot be created. What is being reproduced is
     * the state, which the migration defines exactly: {@code PENDING} with no scan timestamp.
     */
    private AttachmentResponse existingUnscannedAttachment(Scene scene, String filename, byte[] content) {
        AttachmentResponse file = collaboration.attachment(ref(scene), filename, content, scene.adminId());
        jdbc.update(
                "UPDATE attachments SET scan_status = 'PENDING', scanned_at = NULL, scan_signature = NULL "
                        + "WHERE id = ?",
                file.id());
        return file;
    }

    private void assertThatDownloadIsRefused(Scene scene, UUID attachmentId) {
        try {
            mockMvc.perform(get(contentPath(scene, attachmentId))
                            .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        } catch (Exception e) {
            throw new IllegalStateException("the download request itself failed", e);
        }
    }

    private static byte[] infectedContent() {
        return ("harmless looking text " + FakeMalwareScanner.INFECTED_MARKER).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] unscannableContent() {
        return ("harmless looking text " + FakeMalwareScanner.UNSCANNABLE_MARKER).getBytes(StandardCharsets.UTF_8);
    }

    /** Replaces what is in the store, which is how a file whose content differs from its history is made. */
    private void overwriteStoredBytes(UUID attachmentId, byte[] content) {
        try {
            Files.write(storedPath(attachmentId), content);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void deleteStoredBytes(UUID attachmentId) {
        try {
            Files.deleteIfExists(storedPath(attachmentId));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private Path storedPath(UUID attachmentId) {
        String key =
                jdbc.queryForObject("SELECT storage_key FROM attachments WHERE id = ?", String.class, attachmentId);
        return Path.of(storage.localDirectory()).toAbsolutePath().normalize().resolve(key);
    }

    private String scanStatusOf(UUID attachmentId) {
        return jdbc.queryForObject("SELECT scan_status FROM attachments WHERE id = ?", String.class, attachmentId);
    }

    private String signatureOf(UUID attachmentId) {
        return jdbc.queryForObject("SELECT scan_signature FROM attachments WHERE id = ?", String.class, attachmentId);
    }

    private java.sql.Timestamp scannedAtOf(UUID attachmentId) {
        return jdbc.queryForObject(
                "SELECT scanned_at FROM attachments WHERE id = ?", java.sql.Timestamp.class, attachmentId);
    }

    private static String listPath(Scene scene) {
        return "/api/v1/workspaces/" + scene.workspaceId() + "/tasks/" + scene.taskId() + "/attachments";
    }

    private static String metadataPath(Scene scene, UUID attachmentId) {
        return "/api/v1/workspaces/" + scene.workspaceId() + "/attachments/" + attachmentId;
    }

    private static String contentPath(Scene scene, UUID attachmentId) {
        return metadataPath(scene, attachmentId) + "/content";
    }
}
