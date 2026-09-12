package com.company.taskmanagementplatform.reports;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The three widths a trend can be drawn at, and the only three.
 *
 * <p>An enum rather than a string, because the value reaches a native {@code date_trunc} call. Bound
 * as a parameter it is still a value the database interprets, so the set of things it can be has to
 * be closed here rather than checked somewhere on the way. A request parameter never becomes SQL.
 *
 * <p>Each constant knows three things: what {@code date_trunc} calls it, how to move one bucket
 * along, and where the bucket a given day belongs to starts. The last of those is what lets the
 * reports module fill the gaps the database leaves, and having it here keeps the filling and the
 * grouping from disagreeing about which Monday a Wednesday belongs to.
 */
enum TrendGranularity {

    /** One point per day. The default for a window short enough to read that way. */
    DAY("day") {
        @Override
        LocalDate startOfBucket(LocalDate date) {
            return date;
        }

        @Override
        LocalDate next(LocalDate bucketStart) {
            return bucketStart.plusDays(1);
        }
    },

    /**
     * One point per week, starting Monday.
     *
     * <p>Monday because {@code date_trunc('week', …)} in PostgreSQL starts there, following ISO-8601.
     * Java's own idea of the first day of the week depends on the locale, so asking it would make the
     * chart's shape depend on where the server happens to think it is.
     */
    WEEK("week") {
        @Override
        LocalDate startOfBucket(LocalDate date) {
            return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        }

        @Override
        LocalDate next(LocalDate bucketStart) {
            return bucketStart.plusWeeks(1);
        }
    },

    /** One point per calendar month, starting on the first. */
    MONTH("month") {
        @Override
        LocalDate startOfBucket(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        LocalDate next(LocalDate bucketStart) {
            return bucketStart.plusMonths(1);
        }
    };

    /** Beyond this many days a daily chart has more points than a screen has pixels to spare. */
    private static final int DAILY_LIMIT_DAYS = 31;

    private final String sqlField;

    TrendGranularity(String sqlField) {
        this.sqlField = sqlField;
    }

    /** The {@code date_trunc} field name. Never a caller's string. */
    String sqlField() {
        return sqlField;
    }

    abstract LocalDate startOfBucket(LocalDate date);

    abstract LocalDate next(LocalDate bucketStart);

    /**
     * The granularity a window gets when the request names none.
     *
     * <p>A month of days reads well; a year of them does not. Choosing by the width of the window
     * rather than always answering daily is what stops the default answer from being one a client has
     * to throw away and ask again for.
     */
    static TrendGranularity defaultFor(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return days <= DAILY_LIMIT_DAYS ? DAY : WEEK;
    }

    /**
     * @throws BadRequestException naming all three accepted values, the way the task status parser
     *     does. A message that only says the value was wrong leaves the caller guessing at the set
     */
    static TrendGranularity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Granularity must be DAY, WEEK or MONTH.");
        }
    }
}
