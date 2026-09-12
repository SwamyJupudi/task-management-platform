package com.company.taskmanagementplatform.tasks;

/**
 * The four headline numbers every dashboard in the platform shows, for one scope.
 *
 * <p>The definitions are the ones {@code database.md} records and are shared by every figure in
 * phase eight, so a dashboard count and the listing it links to cannot disagree:
 *
 * <ul>
 *   <li><strong>open</strong> is any status but DONE
 *   <li><strong>overdue</strong> is open, with a due date, and that date already past in the
 *       workspace's own timezone. Finished work is never overdue however late it was
 *   <li><strong>done</strong> is the complement of open, so open plus done is always total
 * </ul>
 */
public record TaskCounters(long total, long open, long overdue, long done) {

    static TaskCounters empty() {
        return new TaskCounters(0, 0, 0, 0);
    }
}
