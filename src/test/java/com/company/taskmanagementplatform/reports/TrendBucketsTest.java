package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.reports.dto.TrendPointResponse;
import com.company.taskmanagementplatform.tasks.TaskTrendPoint;

/**
 * The join between what the database returns and what a chart needs.
 *
 * <p>A grouped query returns a row only for a bucket that held something, which is right for it and
 * wrong for a chart: a missing week makes the line jump from the week before to the week after and
 * invites a client to invent the shape between them. These are the cases where the filling could be
 * subtly wrong and nobody would see it on a graph.
 */
class TrendBucketsTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void fillsTheBucketsNeitherSeriesHad() {
        ReportPeriod period = period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));

        List<TrendPointResponse> points = TrendBuckets.merge(
                period,
                TrendGranularity.DAY,
                List.of(new TaskTrendPoint(LocalDate.of(2026, 3, 1), 4)),
                List.of(new TaskTrendPoint(LocalDate.of(2026, 3, 5), 2)));

        assertThat(points).hasSize(5);
        assertThat(points).extracting(TrendPointResponse::created).containsExactly(4L, 0L, 0L, 0L, 0L);
        assertThat(points).extracting(TrendPointResponse::completed).containsExactly(0L, 0L, 0L, 0L, 2L);
    }

    @Test
    void includesBothEndsOfTheWindow() {
        ReportPeriod period = period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        List<TrendPointResponse> points =
                TrendBuckets.merge(period, TrendGranularity.DAY, List.of(), List.of());

        assertThat(points).extracting(TrendPointResponse::bucketStart)
                .containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3));
    }

    @Test
    void mergesTheTwoSeriesOntoOneAxis() {
        ReportPeriod period = period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2));

        List<TrendPointResponse> points = TrendBuckets.merge(
                period,
                TrendGranularity.DAY,
                List.of(new TaskTrendPoint(LocalDate.of(2026, 3, 2), 7)),
                List.of(new TaskTrendPoint(LocalDate.of(2026, 3, 2), 3)));

        assertThat(points.get(1).created()).isEqualTo(7);
        assertThat(points.get(1).completed()).isEqualTo(3);
    }

    @Test
    void startsWeeklyBucketsOnTheSameWeekdayThroughout() {
        // A window opening mid-week opens on the Monday that contains its first day,
        // because that is the bucket the database grouped that day into. Labelling a
        // week by the Wednesday it began would line up with no other chart anywhere.
        ReportPeriod period = period(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 25));

        List<TrendPointResponse> points =
                TrendBuckets.merge(period, TrendGranularity.WEEK, List.of(), List.of());

        assertThat(points).extracting(TrendPointResponse::bucketStart)
                .containsExactly(
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 9),
                        LocalDate.of(2026, 3, 16),
                        LocalDate.of(2026, 3, 23));
    }

    @Test
    void walksMonthsByTheCalendarRatherThanByThirtyDays() {
        ReportPeriod period = period(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10));

        List<TrendPointResponse> points =
                TrendBuckets.merge(period, TrendGranularity.MONTH, List.of(), List.of());

        assertThat(points).extracting(TrendPointResponse::bucketStart)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void returnsOneBucketForAWindowOfOneDay() {
        ReportPeriod period = period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));

        assertThat(TrendBuckets.merge(period, TrendGranularity.DAY, List.of(), List.of()))
                .hasSize(1);
    }

    @Test
    void drawsAFlatLineRatherThanNothingOverAQuietPeriod() {
        // The property the whole class exists for. An empty result would let a
        // client conclude there was no data; a row of zeros says there was no work.
        ReportPeriod period = period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 7));

        List<TrendPointResponse> points =
                TrendBuckets.merge(period, TrendGranularity.DAY, List.of(), List.of());

        assertThat(points).hasSize(7);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.created()).isZero();
            assertThat(point.completed()).isZero();
        });
    }

    private static ReportPeriod period(LocalDate from, LocalDate to) {
        return new ReportPeriod(from, to, UTC);
    }
}
