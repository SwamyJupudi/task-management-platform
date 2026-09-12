package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The allowlists, and the page cap.
 *
 * <p>Written the way {@code TaskSortTest} is written and for the same reason: Spring will sort by any
 * property name it can resolve, which turns a query parameter into a way to probe the shape of an
 * entity and an invitation to order a large table by a column with no index behind it.
 */
class ReportSortsTest {

    private static final Sort FALLBACK = Sort.by(Sort.Direction.DESC, "progress");

    @Test
    void acceptsEveryFieldEachListNames() {
        for (String field : ReportSorts.PROJECTS) {
            assertThatCode(() -> ReportSorts.validate(Sort.by(field), ReportSorts.PROJECTS, FALLBACK))
                    .doesNotThrowAnyException();
        }
        for (String field : ReportSorts.OVERDUE) {
            assertThatCode(() -> ReportSorts.validate(Sort.by(field), ReportSorts.OVERDUE, FALLBACK))
                    .doesNotThrowAnyException();
        }
        for (String field : ReportSorts.WORKLOAD) {
            assertThatCode(() -> ReportSorts.validate(Sort.by(field), ReportSorts.WORKLOAD, FALLBACK))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void refusesAFieldOutsideTheListAndNamesIt() {
        assertThatThrownBy(() -> ReportSorts.validate(Sort.by("ownerUserId"), ReportSorts.PROJECTS, FALLBACK))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ownerUserId");
    }

    @Test
    void refusesAFieldAllowedOnADifferentReport() {
        // The lists are per endpoint rather than shared, so a field that is real
        // somewhere else is still refused here. Sharing one list would quietly let
        // the overdue listing be ordered by a column it has no index for.
        assertThatThrownBy(() -> ReportSorts.validate(Sort.by("progress"), ReportSorts.OVERDUE, FALLBACK))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAsSoonAsOneFieldOfAMultipleSortIsOutsideTheList() {
        Sort mixed = Sort.by("dueDate").and(Sort.by("description"));

        assertThatThrownBy(() -> ReportSorts.validate(mixed, ReportSorts.OVERDUE, FALLBACK))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("description");
    }

    @Test
    void fallsBackWhenNothingWasAskedFor() {
        assertThat(ReportSorts.validate(Sort.unsorted(), ReportSorts.PROJECTS, FALLBACK)).isEqualTo(FALLBACK);
        assertThat(ReportSorts.validate(null, ReportSorts.PROJECTS, FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    void keepsTheRequestedDirection() {
        Sort ascending = Sort.by(Sort.Direction.ASC, "name");

        assertThat(ReportSorts.validate(ascending, ReportSorts.PROJECTS, FALLBACK)).isEqualTo(ascending);
    }

    @Test
    void refusesAPageLargerThanTheCapAndNamesIt() {
        // Refused rather than clamped. A client that asked for two hundred and
        // silently got a hundred would compute the wrong number of pages and stop
        // reading halfway through a report without ever being told.
        assertThatThrownBy(() -> ReportSorts.paged(PageRequest.of(0, 200), ReportSorts.PROJECTS, FALLBACK, 100))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("200");
    }

    @Test
    void acceptsAPageExactlyAtTheCap() {
        assertThatCode(() -> ReportSorts.paged(PageRequest.of(0, 100), ReportSorts.PROJECTS, FALLBACK, 100))
                .doesNotThrowAnyException();
    }

    @Test
    void carriesThePageNumberAndSizeThroughUnchanged() {
        var paged = ReportSorts.paged(PageRequest.of(3, 10), ReportSorts.PROJECTS, FALLBACK, 100);

        assertThat(paged.getPageNumber()).isEqualTo(3);
        assertThat(paged.getPageSize()).isEqualTo(10);
        assertThat(paged.getSort()).isEqualTo(FALLBACK);
    }
}
