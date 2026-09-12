package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.tasks.TaskStatus;

/**
 * What the words in a report mean, pinned.
 *
 * <p>This is the test to keep. "Overdue" is expressed twice in the platform: once as the {@code
 * overdue} filter on the task listing, and once as the predicate every figure in this phase is
 * written against. If those ever disagree, a dashboard count and the list it links to will differ by
 * a row and nobody will be able to tell which of the two is right. Nothing else would notice.
 *
 * <p>This half pins the rule. {@code ReportAccuracyIT} checks the two paths against each other on
 * real data, which is the half that catches a drift in the SQL rather than in the reading of it.
 */
class ReportDefinitionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    @Test
    void openIsEveryStatusButDone() {
        for (TaskStatus status : TaskStatus.values()) {
            assertThat(ReportDefinitions.isOpen(status)).isEqualTo(status != TaskStatus.DONE);
        }
    }

    @Test
    void finishedWorkIsNeverOverdueHoweverLateItWas() {
        // The rule the task listing's overdue filter applies, restated. A list of
        // overdue work is a list of what somebody has to act on; a task delivered
        // three days late last month is not on it.
        assertThat(ReportDefinitions.isOverdue(TaskStatus.DONE, TODAY.minusMonths(1), TODAY))
                .isFalse();
    }

    @Test
    void openWorkPastItsDateIsOverdue() {
        assertThat(ReportDefinitions.isOverdue(TaskStatus.TODO, TODAY.minusDays(1), TODAY))
                .isTrue();
        assertThat(ReportDefinitions.isOverdue(TaskStatus.IN_PROGRESS, TODAY.minusDays(1), TODAY))
                .isTrue();
        assertThat(ReportDefinitions.isOverdue(TaskStatus.REVIEW, TODAY.minusDays(1), TODAY))
                .isTrue();
    }

    @Test
    void workDueTodayIsNotOverdue() {
        // The day is not over. This is the boundary the whole definition turns on
        // and the one a strict inequality in the wrong direction would get wrong.
        assertThat(ReportDefinitions.isOverdue(TaskStatus.TODO, TODAY, TODAY)).isFalse();
    }

    @Test
    void workWithNoDueDateIsNeverOverdue() {
        // Nothing was promised about when it would be done.
        assertThat(ReportDefinitions.isOverdue(TaskStatus.TODO, null, TODAY)).isFalse();
    }

    @Test
    void upcomingIncludesTodayAndTheLastDayOfTheWindow() {
        assertThat(ReportDefinitions.isUpcoming(TaskStatus.TODO, TODAY, TODAY, 7)).isTrue();
        assertThat(ReportDefinitions.isUpcoming(TaskStatus.TODO, TODAY.plusDays(7), TODAY, 7))
                .isTrue();
    }

    @Test
    void upcomingExcludesWhatIsAlreadyLateAndWhatIsBeyondTheWindow() {
        // Late work belongs in the overdue count, not in the plan for the week, and
        // the two panels would otherwise show the same row twice.
        assertThat(ReportDefinitions.isUpcoming(TaskStatus.TODO, TODAY.minusDays(1), TODAY, 7))
                .isFalse();
        assertThat(ReportDefinitions.isUpcoming(TaskStatus.TODO, TODAY.plusDays(8), TODAY, 7))
                .isFalse();
    }

    @Test
    void upcomingExcludesFinishedWorkInsideTheWindow() {
        assertThat(ReportDefinitions.isUpcoming(TaskStatus.DONE, TODAY.plusDays(1), TODAY, 7))
                .isFalse();
    }

    @Test
    void anEmptyDenominatorReadsZeroRatherThanFailing() {
        assertThat(ReportDefinitions.percentage(0, 0)).isZero();
    }

    @Test
    void everythingDoneReadsAHundredRatherThanNinetyNine() {
        // Integer arithmetic, as the progress statement itself computes. Floating
        // point would let an all-finished project read 99 after rounding down.
        assertThat(ReportDefinitions.percentage(3, 3)).isEqualTo(100);
        assertThat(ReportDefinitions.percentage(1, 3)).isEqualTo(33);
    }
}
