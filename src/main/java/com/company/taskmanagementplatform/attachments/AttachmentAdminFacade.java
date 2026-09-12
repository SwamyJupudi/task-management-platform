package com.company.taskmanagementplatform.attachments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the attachments module publishes to {@code admin}, and nothing more.
 *
 * <p>One question, in one query: how many files there are and how much they occupy. It is the only
 * storage figure the platform can produce without asking a provider, and it is worth knowing for
 * exactly that reason.
 *
 * <p><strong>The number counts live rows only, and that understates what is on disk.</strong> A
 * soft-deleted attachment keeps its stored object until the byte purge that does not exist yet, so
 * the store is always at least this large and usually larger. The gap is a known one, recorded under
 * *Still open* in {@code architecture.md}, and this figure is not the place to paper over it: a
 * number that quietly included deleted files would disagree with the file listing an administrator
 * can actually see.
 */
@Service
public class AttachmentAdminFacade {

    private final AttachmentRepository attachments;

    AttachmentAdminFacade(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    /** @return the count and the summed bytes of live attachments */
    @Transactional(readOnly = true)
    public Totals totals() {
        java.util.List<Object[]> rows = attachments.countAndTotalBytes();
        if (rows.isEmpty()) {
            return new Totals(0L, 0L);
        }
        Object[] row = rows.get(0);
        return new Totals(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    /**
     * @param count how many live attachments exist
     * @param totalBytes their summed size. Zero rather than null when there are none, because a
     *     panel showing an empty total should read zero rather than blank
     */
    public record Totals(long count, long totalBytes) {}
}
