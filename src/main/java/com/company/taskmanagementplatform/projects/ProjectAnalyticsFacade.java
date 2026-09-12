package com.company.taskmanagementplatform.projects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the projects module publishes to {@code reports}, and nothing more.
 *
 * <p>A second facade beside {@link ProjectAccessFacade} rather than more methods on it, because the
 * two answer different kinds of question. That one authorizes: its methods ask who the caller is and
 * the guards above them refuse. Nothing here authorizes anything. It is handed a {@link ProjectScope}
 * that has already been resolved and narrowed, and it applies it inside the query. Mixing those on
 * one class would be an invitation to call an unauthorized method from a request path, which is the
 * reasoning phase seven recorded when it split {@code TaskNotificationFacade} off the task guard.
 *
 * <p><strong>Read-only, always.</strong> Nothing here writes, and everything answers with values
 * rather than entities: a facade runs in its own read-only transaction, so an entity handed out of
 * this module would arrive detached and every change made to it silently discarded.
 *
 * <p>Every aggregate is computed in SQL. No method loads a collection of projects so that another
 * module can count it.
 */
@Service
public class ProjectAnalyticsFacade {

    /**
     * Stands in for the empty list when a caller reaches everything.
     *
     * <p>An {@code IN ()} with no members is not valid SQL, and the scope predicate is written as
     * "unrestricted, or one of these". When the first half is true the second is never consulted, so
     * a single identifier that can match nothing is the cheapest thing to put there. The same trick
     * already stands in for "a team with no projects" in the task listing.
     */
    private static final UUID NONE = new UUID(0L, 0L);

    private final ProjectRepository projects;

    ProjectAnalyticsFacade(ProjectRepository projects) {
        this.projects = projects;
    }

    /**
     * How many live projects hold each status, inside the caller's reach.
     *
     * <p>Statuses with no projects are absent from the result rather than present at zero. The
     * reports module fills the gaps, because it is the one that knows a chart needs every column.
     */
    @Transactional(readOnly = true)
    public List<ProjectStatusCount> countsByStatus(UUID workspaceId, ProjectScope scope) {
        if (scope.reachesNothing()) {
            return List.of();
        }

        List<ProjectStatusCount> counts = new ArrayList<>();
        for (Object[] row : projects.countByStatusInScope(workspaceId, scope.unrestricted(), scopeIds(scope))) {
            counts.add(new ProjectStatusCount((ProjectStatus) row[0], ((Number) row[1]).longValue()));
        }
        return List.copyOf(counts);
    }

    /** One row per team that runs at least one project in reach, for the team performance figure. */
    @Transactional(readOnly = true)
    public List<TeamProjectStats> statsByTeam(UUID workspaceId, ProjectScope scope) {
        if (scope.reachesNothing()) {
            return List.of();
        }

        List<TeamProjectStats> stats = new ArrayList<>();
        for (Object[] row : projects.statsByTeamInScope(workspaceId, scope.unrestricted(), scopeIds(scope))) {
            stats.add(new TeamProjectStats(
                    (UUID) row[0], ((Number) row[1]).longValue(), ((Number) row[2]).intValue()));
        }
        return List.copyOf(stats);
    }

    /**
     * A page of projects the caller may read, narrowed by the report's own filters.
     *
     * <p>The scope predicate is built into the same specification as the filters and joined with AND,
     * so no filter can widen it. That is the rule the task and project listings already follow, and
     * repeating it here rather than filtering after the query is what keeps a report from becoming a
     * way past the scope layer.
     */
    @Transactional(readOnly = true)
    public Page<ProjectSummary> page(
            UUID workspaceId,
            ProjectScope scope,
            List<ProjectStatus> statuses,
            UUID teamId,
            UUID ownerUserId,
            Pageable pageable) {

        return projects
                .findAll(matching(workspaceId, scope, statuses, teamId, ownerUserId), pageable)
                .map(ProjectAnalyticsFacade::toSummary);
    }

    /**
     * The reporting facts about a set of projects, keyed by identifier.
     *
     * <p>One query for a whole page. A lookup per row is exactly how a listing becomes N+1 without
     * anybody noticing until production, which is the reason every mapper in this platform is written
     * this way.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ProjectSummary> summariesOf(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ProjectSummary> byId = new LinkedHashMap<>();
        for (Object[] row : projects.findSummaries(projectIds)) {
            ProjectSummary summary = toSummary(row);
            byId.put(summary.projectId(), summary);
        }
        return byId;
    }

    /**
     * The live projects of each named team, keyed by team.
     *
     * <p>One query for every team on the panel rather than one per team. Teams with no projects are
     * absent rather than present with an empty list; the reports module treats a missing entry and an
     * empty one identically, so representing "none" twice would only be a second thing to get wrong.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> projectIdsByTeam(UUID workspaceId, Collection<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<UUID>> byTeam = new LinkedHashMap<>();
        for (Object[] row : projects.findIdsGroupedByTeam(workspaceId, teamIds)) {
            byTeam.computeIfAbsent((UUID) row[0], key -> new ArrayList<>()).add((UUID) row[1]);
        }
        return Map.copyOf(byTeam);
    }

    private static Specification<Project> matching(
            UUID workspaceId,
            ProjectScope scope,
            List<ProjectStatus> statuses,
            UUID teamId,
            UUID ownerUserId) {

        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(builder.equal(root.get("workspaceId"), workspaceId));
            predicates.add(builder.isNull(root.get("deletedAt")));

            // Visibility first, and never optional. A caller who reaches no project gets an
            // empty page rather than an error: they belong to the workspace, there is simply
            // nothing of theirs in it yet.
            if (scope.reachesNothing()) {
                return builder.disjunction();
            }
            if (!scope.unrestricted()) {
                predicates.add(root.get("id").in(scope.projectIds()));
            }

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (teamId != null) {
                predicates.add(builder.equal(root.get("teamId"), teamId));
            }
            if (ownerUserId != null) {
                predicates.add(builder.equal(root.get("ownerUserId"), ownerUserId));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static List<UUID> scopeIds(ProjectScope scope) {
        return scope.unrestricted() ? List.of(NONE) : scope.projectIds();
    }

    private static ProjectSummary toSummary(Project project) {
        return new ProjectSummary(
                project.getId(),
                project.getWorkspaceId(),
                project.getKey(),
                project.getName(),
                project.getStatus(),
                project.getOwnerUserId(),
                project.getTeamId(),
                project.getProgress(),
                project.getUpdatedAt());
    }

    private static ProjectSummary toSummary(Object[] row) {
        return new ProjectSummary(
                (UUID) row[0],
                (UUID) row[1],
                (String) row[2],
                (String) row[3],
                (ProjectStatus) row[4],
                (UUID) row[5],
                (UUID) row[6],
                ((Number) row[7]).intValue(),
                (java.time.Instant) row[8]);
    }
}
