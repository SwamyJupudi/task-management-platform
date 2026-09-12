package com.company.taskmanagementplatform.projects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the projects module publishes to {@code admin}, and nothing more.
 *
 * <p>A third facade beside {@link ProjectAccessFacade} and {@link ProjectAnalyticsFacade}, and the
 * split is the same one phase eight made twice. Those two are handed a {@link ProjectScope} and
 * apply it; <strong>this one has no scope at all</strong>, because the platform administrator's
 * overview crosses every workspace by design.
 *
 * <p>That absence is the whole reason it is a separate class rather than two more methods on the
 * analytics facade. A method that ignored the scope sitting beside methods that apply it is exactly
 * the mistake nobody makes deliberately and nobody notices in review. Here the type has no scope
 * parameter to forget, and the only way in is through {@code admin:read_system}, which no workspace
 * role holds and which {@code @perm.onPlatform} resolves without consulting membership.
 *
 * <p><strong>Read-only, always.</strong> No entity leaves this class, and the listing pages rather
 * than returning everything: an installation's whole project list is precisely the unbounded read
 * the requirements warn about.
 */
@Service
public class ProjectAdminFacade {

    private final ProjectRepository projects;

    ProjectAdminFacade(ProjectRepository projects) {
        this.projects = projects;
    }

    /**
     * How many live projects hold each status, across every workspace.
     *
     * <p>Statuses with no projects are absent, as they are on the workspace-scoped counterpart. The
     * {@code admin} module fills the gaps, because it is the one that knows a panel needs every
     * column.
     */
    @Transactional(readOnly = true)
    public List<ProjectStatusCount> countsByStatusPlatformWide() {
        List<ProjectStatusCount> counts = new ArrayList<>();
        for (Object[] row : projects.countByStatusPlatformWide()) {
            counts.add(new ProjectStatusCount((ProjectStatus) row[0], ((Number) row[1]).longValue()));
        }
        return List.copyOf(counts);
    }

    @Transactional(readOnly = true)
    public long countPlatformWide() {
        return projects.countByDeletedAtIsNull();
    }

    /**
     * The cross-workspace project overview, paged and filtered.
     *
     * <p>The filters narrow; none of them widens, because there is nothing to widen from. A
     * {@code workspaceId} here is an ordinary filter over an already-authorized platform read rather
     * than a scope, which is the distinction the admin module's whole design rests on.
     *
     * <p>Built as a specification rather than a handful of query methods, because four optional
     * filters is where a method per combination stops being reasonable. The existing project listing
     * is written the same way.
     */
    @Transactional(readOnly = true)
    public Page<ProjectSummary> findAllPlatformWide(
            UUID workspaceId, ProjectStatus status, UUID ownerUserId, UUID teamId, Pageable pageable) {

        Specification<Project> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));

            if (workspaceId != null) {
                predicates.add(builder.equal(root.get("workspaceId"), workspaceId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (ownerUserId != null) {
                predicates.add(builder.equal(root.get("ownerUserId"), ownerUserId));
            }
            if (teamId != null) {
                predicates.add(builder.equal(root.get("teamId"), teamId));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };

        return projects.findAll(specification, pageable).map(ProjectAdminFacade::toSummary);
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
}
