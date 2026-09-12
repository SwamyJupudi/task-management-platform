package com.company.taskmanagementplatform.reports;

import java.time.LocalDate;

import com.company.taskmanagementplatform.tasks.TaskStatus;

/**
 * What the words in a report mean, written once so that two figures cannot disagree.
 *
 * <p>The requirements name "open tasks", "overdue tasks" and "upcoming deadlines" and define none of
 * them. These are our definitions, recorded in {@code database.md} beside the project progress rule,
 * and every query in this phase is written to match. They are restated here as code because a
 * definition that lives only in prose is one a new query can quietly depart from.
 *
 * <p><strong>These must agree with the {@code overdue} filter on the task listing.</strong> A
 * dashboard count and the list it links to differing by one row is the worst kind of defect in a
 * reporting feature: it returns 200, it looks right, and nobody can tell which of the two numbers to
 * believe. {@code ReportDefinitionsTest} pins the rule and {@code ReportAccuracyIT} checks the two
 * paths against each other on real data, which is the pair that actually holds them together.
 */
final class ReportDefinitions {

    private ReportDefinitions() {}

    /**
     * Open: any status but DONE.
     *
     * <p>Deliberately the complement of done rather than a list of the other three. A status added
     * later is open until somebody says otherwise, which is the safe direction to be wrong in.
     */
    static boolean isOpen(TaskStatus status) {
        return status != TaskStatus.DONE;
    }

    /**
     * Overdue: open, dated, and that date already past where the company is.
     *
     * <p>Finished work is never overdue, however late it was. That is a choice and worth defending:
     * a list of overdue work is a list of what somebody has to do something about, and a task
     * delivered three days late last month is not on it. History belongs in the trend, not in the
     * count of what is wrong today.
     *
     * <p>A task with no due date is never overdue. Nothing was promised about when it would be done.
     *
     * <p>Due <em>today</em> is not overdue. The day is not over.
     */
    static boolean isOverdue(TaskStatus status, LocalDate dueDate, LocalDate today) {
        return isOpen(status) && dueDate != null && dueDate.isBefore(today);
    }

    /**
     * Upcoming: open, dated, and due between today and the end of the lead window, both included.
     *
     * <p>Today is included rather than excluded. Work due at the end of the day somebody is looking
     * at their dashboard is the single most useful row on it.
     */
    static boolean isUpcoming(TaskStatus status, LocalDate dueDate, LocalDate today, int leadDays) {
        if (!isOpen(status) || dueDate == null) {
            return false;
        }
        return !dueDate.isBefore(today) && !dueDate.isAfter(upcomingWindowEnd(today, leadDays));
    }

    /**
     * The last day the upcoming panel reaches, which is the bound handed to the query.
     *
     * <p>The predicate above and the SQL below it are the same rule read twice, so the arithmetic
     * that decides the far end of the window lives here and both use it. Written out twice, the two
     * would agree until somebody made one of them exclusive.
     */
    static LocalDate upcomingWindowEnd(LocalDate today, int leadDays) {
        return today.plusDays(leadDays);
    }

    /**
     * How many whole days past its date a task is, in the workspace's timezone.
     *
     * <p>Returned rather than left to the client, because a client in another timezone subtracting
     * two dates itself would get a different answer for the same row, and this is the number people
     * sort and escalate by.
     */
    static long daysOverdue(LocalDate dueDate, LocalDate today) {
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
    }

    /** How many whole days until a task is due. Zero means today, which is inside the window. */
    static long daysRemaining(LocalDate today, LocalDate dueDate) {
        return java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
    }

    /**
     * A percentage, computed the way the progress statement computes it.
     *
     * <p>Integer arithmetic with the denominator guarded, so an empty one reads zero rather than
     * throwing or returning null, and a fraction whose parts are equal reads a hundred rather than
     * ninety-nine point nine rounded down. No floating point reaches a figure in this phase.
     */
    static int percentage(long part, long whole) {
        return whole <= 0 ? 0 : (int) ((part * 100L) / whole);
    }
}
