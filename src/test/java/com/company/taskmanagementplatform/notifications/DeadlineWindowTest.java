package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The window arithmetic, and the key that stops the scan repeating itself. */
class DeadlineWindowTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 9);

    @Test
    void theWindowStartsTodayAndIsInclusiveAtBothEnds() {
        DeadlineWindow window = DeadlineWindow.ahead(MONDAY, 2);

        assertThat(window.from()).isEqualTo(MONDAY);
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 3, 11));
    }

    @Test
    void aLeadOfZeroMeansOnlyWhatIsDueToday() {
        DeadlineWindow window = DeadlineWindow.ahead(MONDAY, 0);

        assertThat(window.from()).isEqualTo(MONDAY);
        assertThat(window.to()).isEqualTo(MONDAY);
    }

    @Test
    void overdueWorkIsNotApproaching() {
        // A due date in the past is outside the window by construction. Chasing
        // overdue work is a dashboard's job, and the dashboards are phase eight.
        DeadlineWindow window = DeadlineWindow.ahead(MONDAY, 2);

        assertThat(MONDAY.minusDays(1)).isBefore(window.from());
    }

    @Test
    void aNegativeLeadIsRefusedRatherThanInverted() {
        assertThatThrownBy(() -> DeadlineWindow.ahead(MONDAY, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void daysRemainingCountsCalendarDaysAcrossAMonthBoundary() {
        // A Period's day component would answer this one wrongly.
        DeadlineWindow window = DeadlineWindow.ahead(LocalDate.of(2026, 3, 30), 5);

        assertThat(window.daysRemaining(LocalDate.of(2026, 4, 2))).isEqualTo(3);
        assertThat(window.daysRemaining(LocalDate.of(2026, 3, 30))).isZero();
    }

    @Test
    void theSameTaskAndDueDateProduceTheSameKey() {
        UUID task = UUID.randomUUID();

        assertThat(DeadlineWindow.dedupeKey(task, MONDAY)).isEqualTo(DeadlineWindow.dedupeKey(task, MONDAY));
    }

    @Test
    void aMovedDueDateProducesANewKeyAndNotifiesAgain() {
        UUID task = UUID.randomUUID();

        assertThat(DeadlineWindow.dedupeKey(task, MONDAY))
                .isNotEqualTo(DeadlineWindow.dedupeKey(task, MONDAY.plusDays(2)));
    }

    @Test
    void twoTasksDueTheSameDayDoNotCollide() {
        assertThat(DeadlineWindow.dedupeKey(UUID.randomUUID(), MONDAY))
                .isNotEqualTo(DeadlineWindow.dedupeKey(UUID.randomUUID(), MONDAY));
    }
}
