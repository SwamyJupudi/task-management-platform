package com.company.taskmanagementplatform.projects;

import java.util.List;
import java.util.UUID;

/**
 * Which projects one caller may read, in the form a query in another module can apply.
 *
 * <p>This is the projects module's read scope, published so that tasks can inherit it rather than
 * grow a second one. A task is visible exactly when its project is, so the tasks listing narrows on
 * these identifiers instead of restating the three ways a project comes into reach.
 *
 * <p>{@code unrestricted} is not the same as a list of every project. It means no per-project
 * predicate is needed at all, which keeps the common administrator case from dragging a list of
 * every identifier in the workspace through the query.
 *
 * @param unrestricted true when the caller holds {@code project:read_any}
 * @param projectIds the projects in reach, meaningful only when {@code unrestricted} is false
 */
public record ProjectScope(boolean unrestricted, List<UUID> projectIds) {

    public ProjectScope {
        projectIds = List.copyOf(projectIds);
    }

    public static ProjectScope everything() {
        return new ProjectScope(true, List.of());
    }

    public static ProjectScope of(List<UUID> projectIds) {
        return new ProjectScope(false, projectIds);
    }

    /** True when the caller is restricted and reaches nothing, so a listing must return no rows. */
    public boolean reachesNothing() {
        return !unrestricted && projectIds.isEmpty();
    }
}
