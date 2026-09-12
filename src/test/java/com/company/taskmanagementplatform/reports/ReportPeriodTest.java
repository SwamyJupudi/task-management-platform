package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The one place that decides what a window means, tested on its own.
 *
 * <p>Five endpoints accept a pair of dates and none of them decides anything about the pair. That is
 * only worth doing if the single decision is right, and these are the cases where it could quietly be
 * wrong: an inverted pair, a half-given one, a window wider than the platform will compute, and the
 * timezone the whole thing is read in.
 */
class ReportPeriodTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final ReportProperties PROPERTIES = new ReportProperties(30, 366, 7, 100);

    @Test
    void defaultsToTheConfiguredWindowEndingToday() {
        ReportPeriod period = ReportPeriod.resolve(null, null, TOKYO, PROPERTIES);

        assertThat(period.to()).isEqualTo(LocalDate.now(TOKYO));
        assertThat(period.days()).isEqualTo(30);
    }

    @Test
    void countsBothEndsOfTheWindow() {
        // Inclusive at both ends, because that is what somebody typing two dates
        // means. A window of one day covers one day, not none.
        ReportPeriod period =
                ReportPeriod.resolve(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), TOKYO, PROPERTIES);

        assertThat(period.days()).isEqualTo(1);
    }

    @Test
    void treatsAStartAloneAsRunningUntilToday() {
        ReportPeriod period = ReportPeriod.resolve(LocalDate.now(TOKYO).minusDays(3), null, TOKYO, PROPERTIES);

        assertThat(period.to()).isEqualTo(LocalDate.now(TOKYO));
        assertThat(period.days()).isEqualTo(4);
    }

    @Test
    void treatsAnEndAloneAsTheUsualWindowEndingThere() {
        ReportPeriod period = ReportPeriod.resolve(null, LocalDate.of(2026, 3, 31), TOKYO, PROPERTIES);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void refusesAWindowThatEndsBeforeItStarts() {
        assertThatThrownBy(() ->
                        ReportPeriod.resolve(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1), TOKYO, PROPERTIES))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2026-03-31")
                .hasMessageContaining("2026-03-01");
    }

    @Test
    void refusesAWindowWiderThanTheConfiguredMaximum() {
        // Named rather than clamped. A limit the caller cannot see is one they can
        // only discover by guessing at it.
        assertThatThrownBy(() ->
                        ReportPeriod.resolve(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), TOKYO, PROPERTIES))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("366");
    }

    @Test
    void acceptsAWindowExactlyAtTheMaximum() {
        ReportPeriod period =
                ReportPeriod.resolve(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), TOKYO, PROPERTIES);

        assertThat(period.days()).isEqualTo(365);
    }

    @Test
    void resolvesTheBoundsInTheWorkspaceTimezoneRatherThanInUtc() {
        // The whole reason the zone is carried at all. Tokyo is nine hours ahead, so
        // its day begins at 15:00 the previous day in UTC. A window resolved in UTC
        // would put nine hours of every Tokyo morning in the wrong bucket.
        ReportPeriod period =
                ReportPeriod.resolve(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), TOKYO, PROPERTIES);

        assertThat(period.startInstant()).isEqualTo(Instant.parse("2026-03-01T15:00:00Z"));
        assertThat(period.endInstantExclusive()).isEqualTo(Instant.parse("2026-03-02T15:00:00Z"));
    }

    @Test
    void endsOnAHalfOpenBoundSoNothingOnTheLastDayIsLost() {
        // Exclusive at the top, so a task finished in the final second of the last
        // day is inside the window. An inclusive bound would need to know how
        // precise a timestamp is, and would silently drop whatever was more precise.
        ReportPeriod period =
                ReportPeriod.resolve(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), TOKYO, PROPERTIES);

        assertThat(period.endInstantExclusive())
                .isEqualTo(LocalDate.of(2026, 3, 3).atStartOfDay(TOKYO).toInstant());
    }

    @Test
    void readsTodayInTheWorkspaceTimezone() {
        assertThat(ReportPeriod.resolve(null, null, TOKYO, PROPERTIES).today()).isEqualTo(LocalDate.now(TOKYO));
    }
}
