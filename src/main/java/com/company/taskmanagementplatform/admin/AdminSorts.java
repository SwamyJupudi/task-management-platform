package com.company.taskmanagementplatform.admin;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * What a client may sort an admin listing by, and how big a page it may ask for.
 *
 * <p>Written the way {@code ReportSorts} and {@code TaskQuery.SORTABLE} are written, and for the
 * same reason: Spring will sort by any property name it can resolve, which turns a query parameter
 * into a way to probe the shape of an entity and an invitation to order a large table by a column
 * with nothing behind it. The stakes are higher here than anywhere else, because these tables are
 * not narrowed to one workspace first.
 *
 * <p>A page over the cap is <strong>refused</strong> and not clamped: a client that asked for two
 * hundred and silently received a hundred would compute the wrong number of pages and stop reading
 * halfway through without ever being told.
 */
final class AdminSorts {

    /**
     * The cross-workspace project overview.
     *
     * <p>Only stored columns. {@code workspaceName} is deliberately absent even though the response
     * carries it: the name is resolved from a second module after the page is fetched, so sorting by
     * it would mean sorting a page rather than the table and would order each page independently of
     * the others. That is worse than not offering it.
     */
    static final Set<String> PROJECTS = Set.of("name", "key", "status", "progress", "createdAt", "updatedAt");

    /** The administrative account directory. */
    static final Set<String> ACCOUNTS = Set.of("email", "firstName", "lastName", "status", "createdAt", "lastLoginAt");

    private AdminSorts() {}

    /**
     * The page size bounded, and the ordering left alone.
     *
     * <p>For the audit trail, which offers no sort at all. Newest first is the only order a history
     * is read in, the repository says so in its method name, and a client-supplied sort on top would
     * either duplicate that clause or contradict it. Offering a choice nobody should make is worse
     * than offering none.
     *
     * @throws BadRequestException naming the cap, if the requested size exceeds it
     */
    static Pageable capped(Pageable requested, int maxPageSize) {
        if (requested.getPageSize() > maxPageSize) {
            throw new BadRequestException("An admin page may hold at most " + maxPageSize + " rows; "
                    + requested.getPageSize() + " were asked for.");
        }
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize());
    }

    /** @throws BadRequestException naming the field, exactly as every other listing here does */
    static Sort validate(Sort requested, Set<String> allowed, Sort fallback) {
        if (requested == null || requested.isUnsorted()) {
            return fallback;
        }
        for (Sort.Order order : requested) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("An admin listing cannot be sorted by " + order.getProperty() + ".");
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
            throw new BadRequestException("An admin page may hold at most " + maxPageSize + " rows; "
                    + requested.getPageSize() + " were asked for.");
        }
        return PageRequest.of(
                requested.getPageNumber(), requested.getPageSize(), validate(requested.getSort(), allowed, fallback));
    }
}
