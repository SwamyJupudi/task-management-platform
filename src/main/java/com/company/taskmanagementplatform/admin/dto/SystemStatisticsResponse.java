package com.company.taskmanagementplatform.admin.dto;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The platform at a glance, across every workspace.
 *
 * <p>The requirements name "system statistics" and define nothing, so these definitions are ours and
 * are written down in {@code database.md} beside the report definitions. Every figure counts live
 * rows only: a soft-deleted account, workspace, project or task is gone rather than flagged,
 * everywhere.
 *
 * <p>Assembled in a single read-only transaction, so the panels of one response agree with each
 * other. Two requests may legitimately disagree if somebody finished a task between them.
 *
 * <p><strong>Nothing here is an operational metric.</strong> There is no uptime, no memory, no
 * connection-pool figure, no request rate and no error count. Those belong to Actuator and the log
 * platform, they are not database questions, and answering some of them here would be a second,
 * worse monitoring surface.
 *
 * @param generatedAt when these figures were read, so a client can say how fresh they are. Nothing
 *     is cached, so this is always the moment of the request
 * @param windowDays the trailing window {@code recent} was measured over
 */
@Schema(name = "SystemStatistics", description = "Platform-wide counts across every workspace")
public record SystemStatisticsResponse(
        Instant generatedAt,
        @Schema(example = "30") int windowDays,
        AccountStats accounts,
        WorkspaceStats workspaces,
        WorkStats work,
        StorageStats storage,
        RecentStats recent) {

    /**
     * @param byStatus every status, including those nobody holds. A missing entry would make a client
     *     know the enumeration to draw its chart, and a status that vanished when its last holder was
     *     verified would read as one that never existed
     * @param locked accounts locked out right now. A different fact from {@code DEACTIVATED}: a lock
     *     is a temporary machine decision, a deactivation a durable human one, which is why they live
     *     in different columns. This is usually the figure an administrator is looking for
     */
    @Schema(name = "AccountStats")
    public record AccountStats(long total, Map<String, Long> byStatus, long locked) {}

    /**
     * @param memberships every membership row across the installation. Larger than {@code accounts
     *     .total} whenever anybody belongs to more than one workspace, which is the useful part
     */
    @Schema(name = "WorkspaceStats")
    public record WorkspaceStats(long total, Map<String, Long> byStatus, long teams, long memberships) {}

    /**
     * @param tasksOverdue open work past its due date. The phase eight definition unchanged: finished
     *     work is never overdue however late it was.
     *     <p><strong>Measured in UTC</strong>, unlike every other date comparison in the platform.
     *     Every workspace-scoped figure uses that workspace's own today, but this one spans
     *     workspaces in different zones and there is no single today to use. A workspace that wants
     *     its own answer has the dashboard for it
     */
    @Schema(name = "WorkStats")
    public record WorkStats(
            long projects,
            Map<String, Long> projectsByStatus,
            long tasks,
            Map<String, Long> tasksByStatus,
            long tasksOverdue) {}

    /**
     * @param totalBytes the summed size of live attachments.
     *     <p><strong>This understates what is actually stored.</strong> A soft-deleted attachment
     *     keeps its object until the byte purge that does not exist yet, so the store is always at
     *     least this large. The gap is recorded under *Still open* in {@code architecture.md}; a
     *     figure that quietly included deleted files would disagree with the listing an
     *     administrator can see
     */
    @Schema(name = "StorageStats")
    public record StorageStats(long attachments, long totalBytes) {}

    /**
     * The trailing window.
     *
     * @param accountsCreated new accounts in the window
     * @param accountsSignedIn accounts that signed in during it. Silent accounts are the complement
     * @param auditRowsWritten how much the platform recorded itself doing
     */
    @Schema(name = "RecentStats")
    public record RecentStats(long accountsCreated, long accountsSignedIn, long auditRowsWritten) {}
}
