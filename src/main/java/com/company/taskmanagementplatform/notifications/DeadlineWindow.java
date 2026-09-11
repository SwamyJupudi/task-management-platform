package com.company.taskmanagementplatform.notifications;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The span of dates a scan counts as "approaching", and the key that stops it repeating itself.
 *
 * @param from the first due date that counts, which is today
 * @param to the last, inclusive
 */
record DeadlineWindow(LocalDate from, LocalDate to) {

    /**
     * Today through {@code leadDays} days ahead, inclusive at both ends.
     *
     * <p><strong>It starts today rather than earlier.</strong> A task whose due date has passed is
     * overdue, not approaching, and the requirements name the approaching deadline as the trigger.
     * Chasing overdue work is a dashboard's job, and the dashboards are phase eight.
     *
     * <p>A lead of zero is a legitimate setting and means "only what is due today", which is why the
     * window is inclusive at both ends rather than half-open.
     */
    static DeadlineWindow ahead(LocalDate today, int leadDays) {
        if (leadDays < 0) {
            throw new IllegalArgumentException("A deadline lead cannot be negative: " + leadDays);
        }
        return new DeadlineWindow(today, today.plusDays(leadDays));
    }

    /**
     * How many days somebody has left, which is the one number the message needs.
     *
     * <p>{@code ChronoUnit.DAYS} rather than a {@code Period}, whose day component counts days within
     * a month and would call the last of March and the first of April one day apart in one case and
     * wrong in another.
     */
    int daysRemaining(LocalDate dueDate) {
        return (int) ChronoUnit.DAYS.between(from, dueDate);
    }

    /**
     * The value that makes a second scan write nothing.
     *
     * <p>The task and the date it was due when the message was sent. A scan that runs twice on
     * Monday produces the same key both times and the unique index refuses the second row. A due date
     * moved from Wednesday to Friday produces a different key, so the person is told again, which is
     * right: it is a new deadline.
     *
     * <p>The recipient is not in the key because it is a column of the index. The same task due the
     * same day genuinely does notify two different people, one each.
     */
    static String dedupeKey(UUID taskId, LocalDate dueDate) {
        return taskId + ":" + dueDate;
    }
}
