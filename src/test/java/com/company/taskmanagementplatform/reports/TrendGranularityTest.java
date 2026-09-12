package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The three widths a chart can be drawn at, and the closed set they form.
 *
 * <p>The set has to be closed here because the value reaches a native {@code date_trunc} call. It is
 * bound rather than concatenated, so this is not an injection test; it is a test that no request
 * parameter can ever become a value the database interprets.
 */
class TrendGranularityTest {

    @Test
    void acceptsTheThreeNamesInAnyCase() {
        assertThat(TrendGranularity.parse("day")).isEqualTo(TrendGranularity.DAY);
        assertThat(TrendGranularity.parse("Week")).isEqualTo(TrendGranularity.WEEK);
        assertThat(TrendGranularity.parse("MONTH")).isEqualTo(TrendGranularity.MONTH);
    }

    @Test
    void trimsSurroundingSpace() {
        assertThat(TrendGranularity.parse("  week ")).isEqualTo(TrendGranularity.WEEK);
    }

    @Test
    void treatsAnAbsentValueAsUnstatedRatherThanWrong() {
        // Null means "you choose", which is a different thing from "quarter", and
        // the caller who said nothing should not be shown an error about it.
        assertThat(TrendGranularity.parse(null)).isNull();
        assertThat(TrendGranularity.parse("  ")).isNull();
    }

    @Test
    void refusesAnythingElseAndNamesAllThree() {
        assertThatThrownBy(() -> TrendGranularity.parse("quarter"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DAY")
                .hasMessageContaining("WEEK")
                .hasMessageContaining("MONTH");
    }

    @Test
    void defaultsToDailyForAShortWindowAndWeeklyBeyondIt() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        assertThat(TrendGranularity.defaultFor(start, start.plusDays(30))).isEqualTo(TrendGranularity.DAY);
        assertThat(TrendGranularity.defaultFor(start, start.plusDays(31))).isEqualTo(TrendGranularity.WEEK);
    }

    @Test
    void putsEveryDayOfAWeekInTheSameMondayBucket() {
        // Monday because PostgreSQL's date_trunc('week') starts there, following
        // ISO-8601. Asking Java for the first day of the week would give an answer
        // that depends on the server's locale, and the chart's shape would depend on
        // where the machine thought it was.
        LocalDate monday = LocalDate.of(2026, 3, 2);

        for (int day = 0; day < 7; day++) {
            assertThat(TrendGranularity.WEEK.startOfBucket(monday.plusDays(day))).isEqualTo(monday);
        }
        assertThat(TrendGranularity.WEEK.startOfBucket(monday.plusDays(7))).isEqualTo(monday.plusWeeks(1));
    }

    @Test
    void putsEveryDayOfAMonthInTheSameFirstOfTheMonthBucket() {
        assertThat(TrendGranularity.MONTH.startOfBucket(LocalDate.of(2026, 2, 28)))
                .isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void walksBucketsByCalendarRatherThanByAFixedNumberOfDays() {
        // February is not thirty days long and a week is only seven days long if you
        // start on the right one. Walking the calendar is the only way the filled
        // buckets line up with the ones the database grouped.
        assertThat(TrendGranularity.MONTH.next(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(TrendGranularity.MONTH.next(LocalDate.of(2026, 2, 1))).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void namesTheFieldPostgresUnderstands() {
        assertThat(TrendGranularity.DAY.sqlField()).isEqualTo("day");
        assertThat(TrendGranularity.WEEK.sqlField()).isEqualTo("week");
        assertThat(TrendGranularity.MONTH.sqlField()).isEqualTo("month");
    }
}
