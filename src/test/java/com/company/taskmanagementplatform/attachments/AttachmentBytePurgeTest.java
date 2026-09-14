package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import com.company.taskmanagementplatform.common.scheduling.AdvisoryLock;

/**
 * The one invariant of this job, tested where it can actually be tested: the bytes go before the row,
 * and if the bytes will not go the row stays.
 *
 * <p>A unit test rather than an integration one, because the interesting case is a storage provider
 * that fails, and making the real local store fail on demand would mean interfering with the
 * filesystem in a way that behaves differently on every operating system. Mocks make the failure
 * exact. {@code AttachmentBytePurgeIT} covers the same job against a real store and a real database.
 */
class AttachmentBytePurgeTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(30);

    private final AttachmentRepository attachments = mock(AttachmentRepository.class);
    private final FileStore store = mock(FileStore.class);
    private final AttachmentPurgeProperties properties =
            new AttachmentPurgeProperties(true, "0 15 4 * * *", RETENTION, 2);

    @Test
    void deletesTheObjectBeforeTheRow() {
        Attachment attachment = attachment("workspace/a/task/b/one");
        UUID id = attachment.getId();
        givenPages(List.of(attachment), List.of());

        purge(acquiredLock()).run();

        // If this order were ever reversed, a failure between the two steps would
        // leave bytes in the store that nothing in the schema can name any more.
        InOrder order = Mockito.inOrder(store, attachments);
        order.verify(store).delete("workspace/a/task/b/one");
        order.verify(attachments).deleteById(id);
    }

    @Test
    void keepsTheRowWhenTheObjectWillNotDelete() {
        Attachment attachment = attachment("workspace/a/task/b/stuck");
        givenPages(List.of(attachment), List.of());
        givenStoreFails("workspace/a/task/b/stuck");

        AttachmentBytePurge.Result result = purge(acquiredLock()).run();

        // The row is the only thing that still knows the key. Deleting it would
        // orphan the object permanently.
        verify(attachments, never()).deleteById(any());
        assertThat(result.reclaimed()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void oneUnreachableObjectDoesNotStopTheRest() {
        Attachment stuck = attachment("workspace/a/task/b/stuck");
        Attachment fine = attachment("workspace/a/task/b/fine");
        UUID fineId = fine.getId();
        UUID stuckId = stuck.getId();
        givenPages(List.of(stuck, fine), List.of(stuck));
        givenStoreFails("workspace/a/task/b/stuck");

        AttachmentBytePurge.Result result = purge(acquiredLock()).run();

        verify(attachments).deleteById(fineId);
        verify(attachments, never()).deleteById(stuckId);
        assertThat(result.reclaimed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void stopsWhenAPassReclaimsNothing() {
        // Every remaining row has already failed once, so another pass would be the
        // same failures in the same order. Without this the run would spin to its
        // batch limit doing nothing.
        Attachment stuck = attachment("workspace/a/task/b/stuck");
        when(attachments.findPurgeable(any(), any())).thenReturn(List.of(stuck));
        givenStoreFails("workspace/a/task/b/stuck");

        purge(acquiredLock()).run();

        verify(store, times(1)).delete("workspace/a/task/b/stuck");
    }

    @Test
    void countsARowThatWouldNotDeleteAsAFailureEvenThoughTheBytesAreGone() {
        // Harmless and self-healing: the row is still purgeable and deleting an
        // absent object succeeds, so the next run finishes it. It is still not a
        // success, and the figure should not claim it was.
        Attachment attachment = attachment("workspace/a/task/b/one");
        UUID id = attachment.getId();
        givenPages(List.of(attachment), List.of(attachment));
        doThrow(new DataAccessResourceFailureException("connection lost"))
                .when(attachments)
                .deleteById(id);

        AttachmentBytePurge.Result result = purge(acquiredLock()).run();

        assertThat(result.reclaimed()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenAnotherInstanceHoldsTheLock() {
        Attachment attachment = attachment("workspace/a/task/b/one");
        givenPages(List.of(attachment), List.of());

        AdvisoryLock contended = mock(AdvisoryLock.class);
        when(contended.runExclusively(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(false);

        AttachmentBytePurge.Result result = purge(contended).run();

        verify(store, never()).delete(any());
        verify(attachments, never()).deleteById(any());
        assertThat(result.reclaimed()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void theScheduledEntryPointIsInertWhileTheJobIsSwitchedOff() {
        AttachmentPurgeProperties off = new AttachmentPurgeProperties(false, "0 15 4 * * *", RETENTION, 2);
        AttachmentBytePurge purge =
                new AttachmentBytePurge(attachments, store, acquiredLock(), off, fixedClock());

        purge.scheduled();

        verify(attachments, never()).findPurgeable(any(), any());
    }

    @Test
    void asksOnlyForAttachmentsDeletedBeforeTheRetentionCutoff() {
        givenPages(List.of(), List.of());

        purge(acquiredLock()).run();

        verify(attachments).findPurgeable(eq(NOW.minus(RETENTION)), any(Pageable.class));
    }

    @Test
    void widensThePageByTheFailureSoItStillReachesTheRowsBehindIt() {
        // Batch size is two. The first page is [stuck, fine]; without widening the
        // second page would be the same two rows for ever and the third attachment
        // would never be reached.
        Attachment stuck = attachment("workspace/a/task/b/stuck");
        Attachment fine = attachment("workspace/a/task/b/fine");
        Attachment third = attachment("workspace/a/task/b/third");
        UUID fineId = fine.getId();
        UUID thirdId = third.getId();
        UUID stuckId = stuck.getId();

        when(attachments.findPurgeable(any(), any()))
                .thenReturn(List.of(stuck, fine))
                .thenReturn(List.of(stuck, third))
                .thenReturn(List.of(stuck));
        givenStoreFails("workspace/a/task/b/stuck");

        AttachmentBytePurge.Result result = purge(acquiredLock()).run();

        // The third row is the proof: it sat behind a failure on a page of two, so
        // only a widened page could have reached it.
        verify(attachments).deleteById(fineId);
        verify(attachments).deleteById(thirdId);
        verify(attachments, never()).deleteById(stuckId);
        assertThat(result.reclaimed()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        // Counted once, and the store is not asked twice about the same bad key.
        verify(store, times(1)).delete("workspace/a/task/b/stuck");
    }

    @Test
    void stopsOnceAWholeBatchHasFailedBecauseThatPointsAtTheStoreNotTheFiles() {
        // Two failures with a batch size of two. Hammering an unreachable provider
        // once per remaining row would be worse than waiting for the next run, and
        // the rows survive either way.
        Attachment stuck = attachment("workspace/a/task/b/stuck");
        Attachment alsoStuck = attachment("workspace/a/task/b/also-stuck");
        when(attachments.findPurgeable(any(), any())).thenReturn(List.of(stuck, alsoStuck));
        givenStoreFails("workspace/a/task/b/stuck");
        givenStoreFails("workspace/a/task/b/also-stuck");

        AttachmentBytePurge.Result result = purge(acquiredLock()).run();

        assertThat(result.reclaimed()).isZero();
        assertThat(result.failed()).isEqualTo(2);
        verify(attachments, times(1)).findPurgeable(any(), any());
        verify(attachments, never()).deleteById(any());
    }

    private AttachmentBytePurge purge(AdvisoryLock lock) {
        return new AttachmentBytePurge(attachments, store, lock, properties, fixedClock());
    }

    /** A lock nobody else holds: the work runs here and now. */
    private static AdvisoryLock acquiredLock() {
        AdvisoryLock lock = mock(AdvisoryLock.class);
        when(lock.runExclusively(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });
        return lock;
    }

    private void givenPages(List<Attachment> first, List<Attachment> then) {
        when(attachments.findPurgeable(any(), any())).thenReturn(first).thenReturn(then);
    }

    private void givenStoreFails(String key) {
        doThrow(new UncheckedIOException("the store is unreachable", new IOException()))
                .when(store)
                .delete(key);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static Attachment attachment(String storageKey) {
        Attachment attachment = mock(Attachment.class);
        when(attachment.getId()).thenReturn(UUID.randomUUID());
        when(attachment.getStorageKey()).thenReturn(storageKey);
        return attachment;
    }
}
