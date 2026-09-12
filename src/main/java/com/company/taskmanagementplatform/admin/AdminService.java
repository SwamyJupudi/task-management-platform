package com.company.taskmanagementplatform.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.activity.ActivityService;
import com.company.taskmanagementplatform.admin.dto.PlatformAccountResponse;
import com.company.taskmanagementplatform.admin.dto.PlatformProjectResponse;
import com.company.taskmanagementplatform.admin.dto.SystemStatisticsResponse;
import com.company.taskmanagementplatform.attachments.AttachmentAdminFacade;
import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.ProjectAdminFacade;
import com.company.taskmanagementplatform.projects.ProjectStatus;
import com.company.taskmanagementplatform.projects.ProjectStatusCount;
import com.company.taskmanagementplatform.projects.ProjectSummary;
import com.company.taskmanagementplatform.tasks.TaskAnalyticsFacade;
import com.company.taskmanagementplatform.tasks.TaskStatus;
import com.company.taskmanagementplatform.tasks.TaskStatusCount;
import com.company.taskmanagementplatform.teams.TeamAnalyticsFacade;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAdminFacade;
import com.company.taskmanagementplatform.users.UserQueryService;
import com.company.taskmanagementplatform.users.UserStatus;
import com.company.taskmanagementplatform.workspaces.WorkspaceAdminFacade;
import com.company.taskmanagementplatform.workspaces.WorkspaceStatus;

/**
 * The composition behind the admin panel.
 *
 * <p><strong>This module owns no table, and that is the design.</strong> Every table an admin panel
 * touches already belongs to a module that enforces rules over it: {@code users} owns accounts and
 * is the only package that may hold a password hash, {@code workspaces} owns roles and membership,
 * and so on down. A second module writing those tables would be a second implementation of every
 * rule protecting them. So this is the platform's second composition module after {@code reports},
 * and the pattern is one phase old rather than invented here.
 *
 * <p><strong>Read-only, always.</strong> Every method is a read in one transaction, so the panels of
 * one response agree with each other. The writes the admin panel offers are performed by the modules
 * that own the rows, reached at their own endpoints, and they publish their own events.
 *
 * <p>Nothing here is cached or precomputed. These are the platform's first queries with no tenant
 * predicate, which makes that decision cost more here than it did in phase eight; the bound is that
 * the statistics are a fixed set of counting queries with no client-shaped range beyond a capped
 * window, and that both listings page.
 *
 * <p>Every gap-filling map below is filled here rather than in the facade that produced it, with one
 * exception: an enumeration whose values the owning module holds is filled there, because that is
 * where the list lives and asking for it twice would be a second place for it to go stale. What this
 * class knows is that a chart needs every column.
 */
@Service
public class AdminService {

    private final UserAdminFacade users;
    private final UserQueryService userQueries;
    private final WorkspaceAdminFacade workspaces;
    private final TeamAnalyticsFacade teams;
    private final ProjectAdminFacade projects;
    private final TaskAnalyticsFacade tasks;
    private final AttachmentAdminFacade attachments;
    private final ActivityService activity;
    private final AdminProperties properties;
    private final Clock clock;

    AdminService(
            UserAdminFacade users,
            UserQueryService userQueries,
            WorkspaceAdminFacade workspaces,
            TeamAnalyticsFacade teams,
            ProjectAdminFacade projects,
            TaskAnalyticsFacade tasks,
            AttachmentAdminFacade attachments,
            ActivityService activity,
            AdminProperties properties,
            Clock clock) {
        this.users = users;
        this.userQueries = userQueries;
        this.workspaces = workspaces;
        this.teams = teams;
        this.projects = projects;
        this.tasks = tasks;
        this.attachments = attachments;
        this.activity = activity;
        this.properties = properties;
        this.clock = clock;
    }

    AdminProperties properties() {
        return properties;
    }

    /**
     * Every figure the statistics panel shows, in one transaction.
     *
     * @param windowDays the trailing window for the "recent" figures, or null for the configured
     *     default
     * @throws BadRequestException if the window is not positive or exceeds the cap. Refused rather
     *     than clamped, so a client is never quietly given a different answer from the one it asked
     *     for
     */
    @Transactional(readOnly = true)
    public SystemStatisticsResponse statistics(Integer windowDays) {
        int window = resolveWindow(windowDays);

        Instant now = clock.instant();
        Instant since = now.minus(window, ChronoUnit.DAYS);

        // UTC, and deliberately. Every workspace-scoped figure in the platform
        // uses that workspace's own today; this one spans workspaces in
        // different zones and there is no single today to use. Said on the
        // response rather than left to be inferred.
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        AttachmentAdminFacade.Totals storage = attachments.totals();

        return new SystemStatisticsResponse(
                now,
                window,
                new SystemStatisticsResponse.AccountStats(
                        users.countTotal(), accountsByStatus(), users.countLocked(now)),
                new SystemStatisticsResponse.WorkspaceStats(
                        workspaces.countWorkspaces(),
                        workspacesByStatus(),
                        teams.countTeamsPlatformWide(),
                        workspaces.countMemberships()),
                new SystemStatisticsResponse.WorkStats(
                        projects.countPlatformWide(),
                        projectsByStatus(),
                        tasks.countPlatformWide(),
                        tasksByStatus(),
                        tasks.countOverduePlatformWide(today)),
                new SystemStatisticsResponse.StorageStats(storage.count(), storage.totalBytes()),
                new SystemStatisticsResponse.RecentStats(
                        users.countCreatedSince(since),
                        users.countSignedInSince(since),
                        activity.countSince(since)));
    }

    /**
     * The cross-workspace project overview.
     *
     * <p>Workspace names are resolved for the whole page in one lookup rather than one per row,
     * which is the shape every listing in this platform has had to be written in.
     */
    @Transactional(readOnly = true)
    public PageResponse<PlatformProjectResponse> projectOverview(
            UUID workspaceId, ProjectStatus status, UUID ownerUserId, UUID teamId, Pageable pageable) {

        Page<ProjectSummary> page =
                projects.findAllPlatformWide(workspaceId, status, ownerUserId, teamId, pageable);

        Map<UUID, String> names = workspaces.namesOf(page.getContent().stream()
                .map(ProjectSummary::workspaceId)
                .distinct()
                .toList());

        return PageResponse.of(page, project -> new PlatformProjectResponse(
                project.projectId(),
                project.workspaceId(),
                names.get(project.workspaceId()),
                project.key(),
                project.name(),
                project.status().name(),
                project.ownerUserId(),
                project.teamId(),
                project.progress(),
                project.updatedAt()));
    }

    /**
     * The administrative account directory.
     *
     * <p>The ordinary directory at {@code GET /users} is not duplicated: the search and paging are
     * reused from {@code users}, and this adds the two facts the panel needs, each resolved for the
     * whole page in one query.
     */
    @Transactional(readOnly = true)
    public PageResponse<PlatformAccountResponse> accounts(
            String search, UserStatus status, boolean lockedOnly, Pageable pageable) {

        Page<UserAccount> page = userQueries.search(search, status, lockedOnly, pageable);

        Map<UUID, Long> memberships = workspaces.countMembershipsByUserIds(
                page.getContent().stream().map(UserAccount::id).toList());

        return PageResponse.of(page, account -> new PlatformAccountResponse(
                account.id(),
                account.email(),
                account.firstName(),
                account.lastName(),
                account.status().name(),
                account.isEmailVerified(),
                account.platformRoleId() != null,
                memberships.getOrDefault(account.id(), 0L),
                account.lockedUntil(),
                account.lastLoginAt(),
                account.createdAt()));
    }

    /**
     * The platform audit trail: the rows that belong to no workspace.
     *
     * <p>Delegated whole to {@code activity}, which owns the table. This module adds the
     * authorization and nothing else, which is what a composition module should add.
     */
    @Transactional(readOnly = true)
    public PageResponse<com.company.taskmanagementplatform.activity.dto.ActivityResponse> platformActivity(
            Pageable pageable) {
        return activity.forPlatform(pageable);
    }

    private int resolveWindow(Integer windowDays) {
        if (windowDays == null) {
            return properties.statsWindowDays();
        }
        if (windowDays < 1) {
            throw new BadRequestException("A window must cover at least one day.");
        }
        if (windowDays > properties.maxStatsWindowDays()) {
            throw new BadRequestException("A window may cover at most " + properties.maxStatsWindowDays()
                    + " days; " + windowDays + " were asked for.");
        }
        return windowDays;
    }

    /**
     * The status splits, each with every value present including those nobody holds.
     *
     * <p>A grouped query returns no row for an empty value, which is right for the query and wrong
     * for the chart it feeds: a missing column makes a client know the enumeration to draw the axis,
     * and a value that vanished when its last holder changed would read as one that never existed.
     *
     * <p>Accounts and workspaces are filled by their own modules, because those hold the enumeration
     * already; projects and tasks are filled here, because their facades answer with a list of
     * counts shaped for a scoped report and this is the one caller that wants every column.
     */
    private Map<String, Long> accountsByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<UserStatus, Long> found = users.countByStatus();
        for (UserStatus status : UserStatus.values()) {
            counts.put(status.name(), found.getOrDefault(status, 0L));
        }
        return Map.copyOf(counts);
    }

    private Map<String, Long> workspacesByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<WorkspaceStatus, Long> found = workspaces.countByStatus();
        for (WorkspaceStatus status : WorkspaceStatus.values()) {
            counts.put(status.name(), found.getOrDefault(status, 0L));
        }
        return Map.copyOf(counts);
    }

    private Map<String, Long> projectsByStatus() {
        Map<ProjectStatus, Long> found = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatusCount count : projects.countsByStatusPlatformWide()) {
            found.merge(count.status(), count.count(), Long::sum);
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (ProjectStatus status : ProjectStatus.values()) {
            counts.put(status.name(), found.getOrDefault(status, 0L));
        }
        return Map.copyOf(counts);
    }

    private Map<String, Long> tasksByStatus() {
        Map<TaskStatus, Long> found = new EnumMap<>(TaskStatus.class);
        for (TaskStatusCount count : tasks.countsByStatusPlatformWide()) {
            found.merge(count.status(), count.count(), Long::sum);
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status.name(), found.getOrDefault(status, 0L));
        }
        return Map.copyOf(counts);
    }
}
