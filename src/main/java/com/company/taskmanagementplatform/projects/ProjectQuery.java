package com.company.taskmanagementplatform.projects;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * Turns a filter, a sort and a caller's reach into one query.
 *
 * <p>The visibility predicate is the reason this is built here rather than as a set of repository
 * methods. Scope for reading is not a yes or no at the method boundary; it decides which rows come
 * back. It therefore has to sit inside the query, joined to whatever the caller also filtered on with
 * AND, so that no filter can ever widen it.
 */
final class ProjectQuery {

    /**
     * The only fields a client may sort by.
     *
     * <p>An allowlist rather than passing the request through. Spring will sort by any property name
     * it can resolve, which turns a query parameter into a way to probe the shape of the entity and
     * invites ordering by a column with no index behind it.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name",
            "key", "key",
            "status", "status",
            "priority", "priority",
            "startDate", "startDate",
            "endDate", "endDate",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private ProjectQuery() {}

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
                throw new BadRequestException("Projects cannot be sorted by " + order.getProperty() + ".");
            }
        }
        return requested;
    }

    static Specification<Project> matching(UUID workspaceId, ProjectFilter filter, ProjectVisibility visibility) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(builder.equal(root.get("workspaceId"), workspaceId));
            predicates.add(builder.isNull(root.get("deletedAt")));

            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), filter.priority()));
            }
            if (filter.teamId() != null) {
                predicates.add(builder.equal(root.get("teamId"), filter.teamId()));
            }
            if (filter.ownerUserId() != null) {
                predicates.add(builder.equal(root.get("ownerUserId"), filter.ownerUserId()));
            }
            if (hasText(filter.text())) {
                String pattern = "%" + filter.text().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("key")), pattern)));
            }
            if (hasText(filter.label())) {
                predicates.add(builder.exists(taggedWith(query, builder, root, workspaceId, filter.label())));
            }

            if (!visibility.unrestricted()) {
                predicates.add(builder.or(reachOf(query, builder, root, visibility)));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** EXISTS a tagging of this project with a label of this workspace bearing this folded name. */
    private static Subquery<UUID> taggedWith(
            jakarta.persistence.criteria.CommonAbstractCriteria query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            Root<Project> root,
            UUID workspaceId,
            String labelName) {

        Subquery<UUID> labelIds = query.subquery(UUID.class);
        var label = labelIds.from(Label.class);
        labelIds.select(label.get("id"))
                .where(builder.and(
                        builder.equal(label.get("workspaceId"), workspaceId),
                        builder.equal(
                                builder.lower(builder.trim(label.get("name"))),
                                labelName.trim().toLowerCase(Locale.ROOT))));

        Subquery<UUID> tagging = query.subquery(UUID.class);
        var projectLabel = tagging.from(ProjectLabel.class);
        tagging.select(projectLabel.get("id").get("projectId"))
                .where(builder.and(
                        builder.equal(projectLabel.get("id").get("projectId"), root.get("id")),
                        projectLabel.get("id").get("labelId").in(labelIds)));

        return tagging;
    }

    /** The three ways a project reaches somebody who lacks the workspace-wide read grant. */
    private static Predicate[] reachOf(
            jakarta.persistence.criteria.CommonAbstractCriteria query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            Root<Project> root,
            ProjectVisibility visibility) {

        List<Predicate> reach = new ArrayList<>();
        reach.add(builder.equal(root.get("ownerUserId"), visibility.userId()));

        Subquery<UUID> membership = query.subquery(UUID.class);
        var member = membership.from(ProjectMember.class);
        membership.select(member.get("projectId"))
                .where(builder.and(
                        builder.equal(member.get("projectId"), root.get("id")),
                        builder.equal(member.get("userId"), visibility.userId())));
        reach.add(builder.exists(membership));

        if (!visibility.ledTeamIds().isEmpty()) {
            reach.add(root.get("teamId").in(visibility.ledTeamIds()));
        }

        return reach.toArray(Predicate[]::new);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
