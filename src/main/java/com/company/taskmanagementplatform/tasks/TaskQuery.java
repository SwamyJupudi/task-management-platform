package com.company.taskmanagementplatform.tasks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.projects.ProjectScope;

/**
 * Turns a filter, a sort and a caller's reach into one query.
 *
 * <p>The visibility predicate is the reason this is built here rather than as a set of repository
 * methods. Scope for reading is not a yes or no at the method boundary; it decides which rows come
 * back. It therefore has to sit inside the query, joined to whatever the caller also filtered on
 * with AND, so that no filter can ever widen it.
 *
 * <p>Task reach is project reach. A caller without {@code project:read_any} arrives here with the
 * projects they own, belong to, or lead the team of already resolved, and every task outside them is
 * simply not in the result. That is what keeps task visibility and project visibility from drifting
 * apart: there is one rule and this applies it rather than restating it.
 */
final class TaskQuery {

    /**
     * The only fields a client may sort by.
     *
     * <p>An allowlist rather than passing the request through. Spring will sort by any property name
     * it can resolve, which turns a query parameter into a way to probe the shape of the entity and
     * invites ordering by a column with no index behind it.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            "taskNumber", "taskNumber",
            "title", "title",
            "status", "status",
            "priority", "priority",
            "dueDate", "dueDate",
            "startDate", "startDate",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "boardPosition", "boardPosition");

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private TaskQuery() {}

    static Set<String> sortableFields() {
        return SORTABLE.keySet();
    }

    /**
     * @throws BadRequestException naming the field, if a sort mentions anything outside the allowlist
     */
    static Sort validateSort(Sort requested) {
        if (requested == null || requested.isUnsorted()) {
            return NEWEST_FIRST;
        }
        for (Sort.Order order : requested) {
            if (!SORTABLE.containsKey(order.getProperty())) {
                throw new BadRequestException("Tasks cannot be sorted by " + order.getProperty() + ".");
            }
        }
        return requested;
    }

    static Specification<Task> matching(UUID workspaceId, TaskFilter filter, ProjectScope scope, LocalDate today) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(builder.equal(root.get("workspaceId"), workspaceId));
            predicates.add(builder.isNull(root.get("deletedAt")));

            // Visibility first, and never optional. A caller who reaches no project
            // gets an empty page rather than an error: they belong to the workspace,
            // there is simply nothing of theirs in it yet.
            if (scope.reachesNothing()) {
                return builder.disjunction();
            }
            if (!scope.unrestricted()) {
                predicates.add(root.get("projectId").in(scope.projectIds()));
            }

            // The same for a label nobody in this workspace has ever used. Ignoring
            // an unknown label would quietly return every task instead of none.
            if (filter.labelUnknown()) {
                return builder.disjunction();
            }

            if (!filter.projectIds().isEmpty()) {
                predicates.add(root.get("projectId").in(filter.projectIds()));
            }
            if (filter.unassigned()) {
                predicates.add(builder.isNull(root.get("assigneeUserId")));
            } else if (filter.assigneeUserId() != null) {
                predicates.add(builder.equal(root.get("assigneeUserId"), filter.assigneeUserId()));
            }
            if (filter.reporterUserId() != null) {
                predicates.add(builder.equal(root.get("reporterUserId"), filter.reporterUserId()));
            }
            if (!filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (!filter.priorities().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priorities()));
            }
            if (filter.dueOn() != null) {
                predicates.add(builder.equal(root.get("dueDate"), filter.dueOn()));
            }
            if (filter.dueBefore() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("dueDate"), filter.dueBefore()));
            }
            if (filter.dueAfter() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("dueDate"), filter.dueAfter()));
            }
            if (filter.overdue()) {
                // Finished work is never overdue, however late it was.
                predicates.add(builder.lessThan(root.get("dueDate"), today));
                predicates.add(builder.notEqual(root.get("status"), TaskStatus.DONE));
            }
            if (filter.createdBefore() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), filter.createdBefore()));
            }
            if (filter.createdAfter() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filter.createdAfter()));
            }
            if (filter.labelId() != null) {
                predicates.add(builder.exists(labelledWith(query, builder, root, filter.labelId())));
            }
            if (filter.blocked()) {
                predicates.add(builder.exists(blockedByUnfinished(query, builder, root)));
            }

            Predicate search = searchPredicate(builder, root, filter);
            if (search != null) {
                predicates.add(search);
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Free text over the title, plus the task addressed directly as {@code PROJ-12}.
     *
     * <p>The key form is resolved to a project and a number before it gets here, so matching it costs
     * two column comparisons rather than a join to the projects table.
     */
    private static Predicate searchPredicate(CriteriaBuilder builder, Root<Task> root, TaskFilter filter) {
        List<Predicate> matches = new ArrayList<>();

        if (filter.text() != null && !filter.text().isBlank()) {
            String pattern = "%" + filter.text().trim().toLowerCase(Locale.ROOT) + "%";
            matches.add(builder.like(builder.lower(root.get("title")), pattern));
        }
        if (filter.keyMatch() != null) {
            matches.add(builder.and(
                    builder.equal(root.get("projectId"), filter.keyMatch().projectId()),
                    builder.equal(root.get("taskNumber"), filter.keyMatch().taskNumber())));
        }

        return matches.isEmpty() ? null : builder.or(matches.toArray(Predicate[]::new));
    }

    /** EXISTS a labelling of this task with the label the filter named. */
    private static Subquery<UUID> labelledWith(
            jakarta.persistence.criteria.CommonAbstractCriteria query,
            CriteriaBuilder builder,
            Root<Task> root,
            UUID labelId) {

        Subquery<UUID> labelling = query.subquery(UUID.class);
        var taskLabel = labelling.from(TaskLabel.class);
        labelling.select(taskLabel.get("id").get("taskId"))
                .where(builder.and(
                        builder.equal(taskLabel.get("id").get("taskId"), root.get("id")),
                        builder.equal(taskLabel.get("id").get("labelId"), labelId)));

        return labelling;
    }

    /**
     * EXISTS a dependency of this task on a task that is not finished.
     *
     * <p>A dependency on something already done blocks nothing, so "blocked" means waiting on live,
     * unfinished work rather than merely having an edge.
     */
    private static Subquery<UUID> blockedByUnfinished(
            jakarta.persistence.criteria.CommonAbstractCriteria query, CriteriaBuilder builder, Root<Task> root) {

        // Two subqueries rather than a join, because the dependency entity holds
        // identifiers rather than associations, matching the convention everywhere
        // else in this schema.
        Subquery<UUID> unfinished = query.subquery(UUID.class);
        var blockingTask = unfinished.from(Task.class);
        unfinished.select(blockingTask.get("id"))
                .where(builder.and(
                        builder.isNull(blockingTask.get("deletedAt")),
                        builder.notEqual(blockingTask.get("status"), TaskStatus.DONE)));

        Subquery<UUID> blockers = query.subquery(UUID.class);
        var dependency = blockers.from(TaskDependency.class);
        blockers.select(dependency.get("taskId"))
                .where(builder.and(
                        builder.equal(dependency.get("taskId"), root.get("id")),
                        dependency.get("dependsOnTaskId").in(unfinished)));

        return blockers;
    }
}
