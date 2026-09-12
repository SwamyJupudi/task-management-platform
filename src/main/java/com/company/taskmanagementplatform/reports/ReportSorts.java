package com.company.taskmanagementplatform.reports;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * What a client may sort a report by, and how big a page it may ask for.
 *
 * <p>An allowlist per listing, written the way {@code TaskQuery.SORTABLE} is written and for the
 * same reason. Spring will sort by any property name it can resolve, which turns a query parameter
 * into a way to probe the shape of an entity and an invitation to order a large table by a column
 * with nothing behind it. Passing the request through would undo the point of the indexes this phase
 * brought.
 *
 * <p>The page cap is here rather than in each controller because it is the same decision every time.
 * A size over it is <strong>refused</strong> and not clamped: a client that asked for two hundred and
 * silently received a hundred would compute the wrong number of pages and stop reading halfway
 * through a report without ever being told.
 */
final class ReportSorts {

    /** The project report. Progress descending by default: the finished work is what is reported on. */
    static final Set<String> PROJECTS = Set.of("name", "key", "status", "progress", "updatedAt");

    /** The overdue listing. Due date ascending by default, so the longest overdue is at the top. */
    static final Set<String> OVERDUE = Set.of("dueDate", "priority", "projectId", "title");

    /**
     * The workload listing, whose fields are computed rather than stored.
     *
     * <p>Sorted in the reports module rather than in SQL, deliberately. The aggregate returns one row
     * per person, so the collection being ordered is the workspace roster rather than the task table,
     * and ordering it here costs nothing. The alternative, an {@code ORDER BY} over an aggregate
     * alias chosen by a query parameter, cannot be written without building SQL from a string.
     */
    static final Set<String> WORKLOAD = Set.of("open", "overdue", "completedInPeriod", "fullName");

    private ReportSorts() {}

    /**
     * @throws BadRequestException naming the field, exactly as the task listing does for its own
     */
    static Sort validate(Sort requested, Set<String> allowed, Sort fallback) {
        if (requested == null || requested.isUnsorted()) {
            return fallback;
        }
        for (Sort.Order order : requested) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("A report cannot be sorted by " + order.getProperty() + ".");
            }
        }
        return requested;
    }

    /**
     * The requested page, with its sort checked and its size bounded.
     *
     * @throws BadRequestException naming the cap, if the requested size exceeds it
     */
    static Pageable paged(Pageable requested, Set<String> allowed, Sort fallback, int maxPageSize) {
        if (requested.getPageSize() > maxPageSize) {
            throw new BadRequestException(
                    "A report page may hold at most " + maxPageSize + " rows; " + requested.getPageSize()
                            + " were asked for.");
        }
        return PageRequest.of(
                requested.getPageNumber(),
                requested.getPageSize(),
                validate(requested.getSort(), allowed, fallback));
    }
}
