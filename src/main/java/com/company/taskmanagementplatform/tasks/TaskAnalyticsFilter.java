package com.company.taskmanagementplatform.tasks;

import java.time.Instant;
import java.util.UUID;

/**
 * The narrowing a distribution accepts beyond the caller's own reach. Every field is optional; a
 * null one narrows nothing.
 *
 * <p>Filters compose with the scope rather than replacing it. The scope predicate and these are
 * joined with AND inside one query, so narrowing by person or by period can never reveal a task the
 * caller was not already entitled to see. That is the rule the project and task listings follow, and
 * repeating it here is what stops a report becoming a way around it.
 *
 * <p>The window is over <strong>creation</strong>, not completion. "How is the work we took on this
 * month distributed" is the question a status breakdown answers; completion has its own report,
 * where it is the subject rather than a filter.
 *
 * <p>Instants rather than dates, because the caller resolves the workspace's timezone once and hands
 * down the absolute bounds. Resolving it again here would be a second place for it to be wrong.
 */
public record TaskAnalyticsFilter(UUID assigneeUserId, Instant createdFrom, Instant createdTo) {

    public static TaskAnalyticsFilter none() {
        return new TaskAnalyticsFilter(null, null, null);
    }

    public static TaskAnalyticsFilter assignedTo(UUID userId) {
        return new TaskAnalyticsFilter(userId, null, null);
    }
}
