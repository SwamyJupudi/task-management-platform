package com.company.taskmanagementplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The allowlist and the page cap, which are the two things standing between a query parameter and an
 * unbounded read over a table with no tenant predicate.
 *
 * <p>The stakes are higher here than for the reports, which is worth stating: every other listing in
 * the platform is narrowed to one workspace before any of this applies.
 */
class AdminSortsTest {

    @Test
    void fallsBackWhenNoSortIsRequested() {
        Sort fallback = Sort.by("updatedAt");

        assertThat(AdminSorts.validate(Sort.unsorted(), AdminSorts.PROJECTS, fallback)).isEqualTo(fallback);
        assertThat(AdminSorts.validate(null, AdminSorts.PROJECTS, fallback)).isEqualTo(fallback);
    }

    @Test
    void acceptsAnAllowlistedField() {
        Sort requested = Sort.by(Sort.Direction.DESC, "progress");

        assertThat(AdminSorts.validate(requested, AdminSorts.PROJECTS, Sort.unsorted()))
                .isEqualTo(requested);
    }

    @Test
    void refusesAFieldOutsideTheAllowlistAndNamesIt() {
        // Spring will sort by any property it can resolve, which turns this
        // parameter into a way to probe the shape of an entity.
        assertThatThrownBy(() ->
                        AdminSorts.validate(Sort.by("description"), AdminSorts.PROJECTS, Sort.unsorted()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("description");
    }

    @Test
    void refusesTheWorkspaceNameEvenThoughTheResponseCarriesIt() {
        // It is resolved from a second module after the page is fetched, so sorting
        // by it would order each page independently of the others. Offering it
        // would be worse than not offering it.
        assertThatThrownBy(() ->
                        AdminSorts.validate(Sort.by("workspaceName"), AdminSorts.PROJECTS, Sort.unsorted()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAPageLargerThanTheCapRatherThanClampingIt() {
        // A client that asked for two hundred and silently received a hundred would
        // compute the wrong number of pages and stop reading halfway through.
        assertThatThrownBy(() ->
                        AdminSorts.paged(PageRequest.of(0, 200), AdminSorts.ACCOUNTS, Sort.unsorted(), 100))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("200");
    }

    @Test
    void acceptsAPageAtTheCap() {
        assertThat(AdminSorts.paged(PageRequest.of(2, 100), AdminSorts.ACCOUNTS, Sort.unsorted(), 100)
                        .getPageSize())
                .isEqualTo(100);
    }

    @Test
    void cappedBoundsTheSizeAndLeavesTheOrderingAlone() {
        // The audit trail offers no sort: newest first is the only order a history
        // is read in, and the repository's own method name says so.
        assertThat(AdminSorts.capped(PageRequest.of(1, 10, Sort.by("createdAt")), 25)
                        .getSort()
                        .isSorted())
                .isFalse();

        assertThatThrownBy(() -> AdminSorts.capped(PageRequest.of(0, 26), 25))
                .isInstanceOf(BadRequestException.class);
    }
}
