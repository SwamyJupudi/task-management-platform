package com.company.taskmanagementplatform.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The stretch of time a report is asked about, resolved once and in the right timezone.
 *
 * <p>One place, so no endpoint invents its own window rule. Five of them accept a pair of dates and
 * every one of them would otherwise have to decide what an absent pair means, what an inverted one
 * means, and how wide is too wide. Three answers repeated five times is three answers that drift.
 *
 * <p><strong>Resolved in the workspace's own timezone, never in UTC.</strong> "The last thirty days"
 * ends at the end of today where the company is, and a report that ran the boundary at midnight UTC
 * would put a Friday evening's work in Saturday for most of the world. The zone is resolved once per
 * request by {@code WorkspaceSettingsFacade} and handed here; resolving it again further down would
 * be a second place for it to be wrong.
 *
 * <p>Both ends are inclusive as dates, which is what somebody typing them means. The instants are
 * half-open, because that is what a range query wants: {@code >= from} and {@code < to} needs no
 * knowledge of how precise a timestamp is, whereas {@code <= to} would silently drop anything
 * finished in the last second of the final day.
 *
 * @param from the first day counted, inclusive
 * @param to the last day counted, inclusive
 * @param zone the workspace's timezone, in which both are read
 */
record ReportPeriod(LocalDate from, LocalDate to, ZoneId zone) {

    /**
     * The window a request asked for, or the default one, refusing anything unusable.
     *
     * <p>A half-given pair is completed rather than refused: naming only a start means "from then
     * until now", and naming only an end means "the usual window ending there". Both are ordinary
     * things to ask and neither is ambiguous.
     *
     * @throws BadRequestException if the end precedes the start, or the span exceeds the configured
     *     maximum. Both messages name the numbers involved, because a limit the caller cannot see is
     *     one they can only discover by guessing
     */
    static ReportPeriod resolve(LocalDate from, LocalDate to, ZoneId zone, ReportProperties properties) {
        LocalDate today = LocalDate.now(zone);

        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(properties.defaultPeriodDays() - 1L);

        if (end.isBefore(start)) {
            throw new BadRequestException("The end of the period (" + end + ") is before its start (" + start + ").");
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > properties.maxPeriodDays()) {
            throw new BadRequestException(
                    "A report may cover at most " + properties.maxPeriodDays() + " days; that one covers " + days
                            + ".");
        }

        return new ReportPeriod(start, end, zone);
    }

    /** The first instant of the first day, in the workspace's timezone. */
    Instant startInstant() {
        return from.atStartOfDay(zone).toInstant();
    }

    /** The first instant after the last day: the exclusive upper bound of a half-open range. */
    Instant endInstantExclusive() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** How many days the window covers, both ends counted. */
    long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Today, where the company is. What "overdue" and "upcoming" are measured against. */
    LocalDate today() {
        return LocalDate.now(zone);
    }
}
