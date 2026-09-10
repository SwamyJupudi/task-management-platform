package com.company.taskmanagementplatform.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The filters a task listing accepts. Every field is optional; a null or empty one narrows nothing.
 *
 * <p>Filters compose with the caller's visibility rather than replacing it, so narrowing by project,
 * assignee or team can never reveal a task the caller was not entitled to see. The visibility
 * predicate is joined to all of this with AND, inside the query, precisely so that no filter can
 * widen it.
 *
 * <p>{@code labelId} and {@code keyMatch} arrive resolved rather than as raw text. A tag name and a
 * {@code PROJ-12} search both have to become identifiers before the query can use them, and
 * resolving them once in the service is cheaper and clearer than a join per row.
 *
 * @param projectIds narrows to these projects; empty narrows nothing
 * @param unassigned true to ask for tasks with no assignee, which no identifier can express
 * @param overdue true to ask for tasks past their due date and not finished
 * @param labelId the label to require, already resolved from its folded name
 * @param labelUnknown true when a label name was given and the workspace has no such label
 * @param keyMatch a task named directly, as {@code PROJ-12}, resolved to a project and a number
 */
record TaskFilter(
        List<UUID> projectIds,
        UUID assigneeUserId,
        boolean unassigned,
        UUID reporterUserId,
        List<TaskStatus> statuses,
        List<TaskPriority> priorities,
        LocalDate dueBefore,
        LocalDate dueAfter,
        LocalDate dueOn,
        boolean overdue,
        Instant createdBefore,
        Instant createdAfter,
        UUID labelId,
        boolean labelUnknown,
        boolean blocked,
        String text,
        TaskKeyMatch keyMatch) {

    TaskFilter {
        projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        priorities = priorities == null ? List.of() : List.copyOf(priorities);
    }

    static TaskFilter none() {
        return new TaskFilter(
                List.of(), null, false, null, List.of(), List.of(), null, null, null, false, null, null, null, false,
                false, null, null);
    }

    /** One task addressed by its rendered key, split back into the two columns that store it. */
    record TaskKeyMatch(UUID projectId, int taskNumber) {}
}
