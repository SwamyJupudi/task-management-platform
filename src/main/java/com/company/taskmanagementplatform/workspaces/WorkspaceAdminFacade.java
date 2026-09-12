package com.company.taskmanagementplatform.workspaces;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the workspaces module publishes to {@code admin}, and nothing more.
 *
 * <p>A facade beside the four services this module already has, for the reason every facade in the
 * platform gives: those map to types this module owns and serve its own controllers, while these
 * answer with counts another module composes into a different shape. Nothing here authorizes
 * anything; the caller has already passed a platform permission check.
 *
 * <p><strong>Read-only, always.</strong> No entity leaves, and every aggregate is computed in SQL.
 *
 * <p>These are the platform's first counts with no workspace predicate, which is worth naming: every
 * other aggregate in the platform is narrowed to one tenant by construction, and these deliberately
 * are not. That is what {@code admin:read_system} gates.
 */
@Service
public class WorkspaceAdminFacade {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;

    WorkspaceAdminFacade(WorkspaceRepository workspaces, WorkspaceMemberRepository members) {
        this.workspaces = workspaces;
        this.members = members;
    }

    /**
     * How many live workspaces hold each status.
     *
     * <p>Every status is present, including one nobody holds, for the reason
     * {@code MembershipService.countMembersByRole} already records: a chart with a missing column
     * makes its client know the enumeration, and a value that vanished when its last holder changed
     * would read as one that never existed.
     */
    @Transactional(readOnly = true)
    public Map<WorkspaceStatus, Long> countByStatus() {
        Map<WorkspaceStatus, Long> counts = new EnumMap<>(WorkspaceStatus.class);
        for (WorkspaceStatus status : WorkspaceStatus.values()) {
            counts.put(status, 0L);
        }

        for (Object[] row : workspaces.countByStatusGrouped()) {
            counts.merge((WorkspaceStatus) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        return Map.copyOf(counts);
    }

    @Transactional(readOnly = true)
    public long countWorkspaces() {
        return workspaces.countByDeletedAtIsNull();
    }

    /**
     * Every membership row across the installation.
     *
     * <p>Not the same number as the account count, and the difference is the useful part: one person
     * may belong to several workspaces, so this exceeds the headcount whenever anybody does.
     */
    @Transactional(readOnly = true)
    public long countMemberships() {
        return members.count();
    }

    /**
     * How many workspaces each of these accounts belongs to, in one query.
     *
     * <p>For the administrative account listing, which shows it beside each row. One grouped query
     * for a whole page rather than one per row, which is the shape every listing in this platform
     * has had to be written in.
     *
     * @return counts by user identifier; an account belonging to nothing is simply absent
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> countMembershipsByUserIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : members.countByUserIdsGrouped(userIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /** The names of a set of workspaces, for a page of projects that spans several. */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> names = new HashMap<>();
        for (Workspace workspace : workspaces.findAllByIdInAndDeletedAtIsNull(workspaceIds)) {
            names.put(workspace.getId(), workspace.getName());
        }
        return names;
    }
}
