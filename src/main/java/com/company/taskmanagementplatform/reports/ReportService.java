package com.company.taskmanagementplatform.reports;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.ProjectAnalyticsFacade;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.projects.ProjectStatus;
import com.company.taskmanagementplatform.projects.ProjectSummary;
import com.company.taskmanagementplatform.reports.dto.DistributionResponse;
import com.company.taskmanagementplatform.reports.dto.OverdueTaskResponse;
import com.company.taskmanagementplatform.reports.dto.ProjectProgressResponse;
import com.company.taskmanagementplatform.reports.dto.TrendPointResponse;
import com.company.taskmanagementplatform.reports.dto.WorkloadResponse;
import com.company.taskmanagementplatform.tasks.AssigneeTaskCounts;
import com.company.taskmanagementplatform.tasks.ProjectTaskCounts;
import com.company.taskmanagementplatform.tasks.TaskAnalyticsFacade;
import com.company.taskmanagementplatform.tasks.TaskAnalyticsFilter;
import com.company.taskmanagementplatform.tasks.TaskReportRow;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.WorkspaceSettingsFacade;

/**
 * The five reports, composed from what the owning modules answer.
 *
 * <p>This class owns no table and reads none. Every aggregate is computed in SQL by the module that
 * holds the rows, and what happens here is composition: stitching one module's counts onto another's
 * names, filling the gaps a grouped query leaves, and turning identifiers into the keys and names
 * people read. That division is the whole design of the phase, and it is why {@code reports} has a
 * service and a controller and no repository.
 *
 * <p><strong>Every method is read-only and nothing here is cached.</strong> Each figure is therefore
 * as current as the transaction that produced it, and figures within one response are mutually
 * consistent because they share that transaction. Two separate requests may legitimately disagree if
 * somebody finished a task between them, which is the correct behaviour and worth knowing about.
 *
 * <p>The caller's scope arrives already resolved and is passed straight into the aggregate queries.
 * It is never applied to a result afterwards. A predicate applied after aggregation would return 200
 * and look correct while counting work the caller cannot see.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final TaskAnalyticsFacade tasks;
    private final ProjectAnalyticsFacade projects;
    private final UserAccountService users;
    private final WorkspaceSettingsFacade settings;
    private final ReportProperties properties;

    ReportService(
            TaskAnalyticsFacade tasks,
            ProjectAnalyticsFacade projects,
            UserAccountService users,
            WorkspaceSettingsFacade settings,
            ReportProperties properties) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
        this.settings = settings;
        this.properties = properties;
    }

    // --- the shared vocabulary ---------------------------------------------

    /**
     * The workspace's timezone, resolved once per request by whoever is about to ask a question of
     * time.
     *
     * <p>Exposed so a controller resolves it once and hands the same zone to every call it makes.
     * Reading it again per query would let two panels of one dashboard disagree about what day it is,
     * which is a race nobody would ever reproduce.
     */
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID workspaceId) {
        return settings.zoneOf(workspaceId);
    }

    ReportProperties properties() {
        return properties;
    }

    // --- GET /reports/projects ---------------------------------------------

    /**
     * Project completion and progress, paged.
     *
     * <p>Two queries whatever the page size: one for the page of projects, one for the task counts of
     * every project on it. The counts are asked for by identifier, so the page itself is what bounds
     * the work rather than the size of the workspace.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProjectProgressResponse> projectReport(
            UUID workspaceId,
            ProjectScope scope,
            List<String> statuses,
            UUID teamId,
            UUID ownerUserId,
            LocalDate today,
            Pageable pageable) {

        log.debug("Project report for workspace {} as of {}", workspaceId, today);

        Page<ProjectSummary> page =
                projects.page(workspaceId, scope, parseStatuses(statuses), teamId, ownerUserId, pageable);

        Map<UUID, ProjectTaskCounts> counts = taskCountsOf(page.getContent(), today);

        return PageResponse.of(page, project -> ReportMapper.project(project, countsFor(counts, project)));
    }

    // --- GET /reports/tasks/distribution -----------------------------------

    /**
     * Task status and priority distribution over a scope and an optional window.
     *
     * <p>The window is over creation rather than completion. "How is the work we took on this month
     * spread" is the question a status breakdown answers; completion has the trend report, where it is
     * the subject rather than a filter.
     */
    @Transactional(readOnly = true)
    public DistributionResponse distribution(
            UUID workspaceId, ProjectScope scope, UUID assigneeUserId, ReportPeriod period, boolean windowed) {

        TaskAnalyticsFilter filter = windowed
                ? new TaskAnalyticsFilter(assigneeUserId, period.startInstant(), period.endInstantExclusive())
                : new TaskAnalyticsFilter(assigneeUserId, null, null);

        return ReportMapper.distribution(
                tasks.countsByStatus(workspaceId, scope, filter), tasks.countsByPriority(workspaceId, scope, filter));
    }

    // --- GET /reports/tasks/overdue ----------------------------------------

    /**
     * Everything late, paged and sorted within the allowlist.
     *
     * <p>Three queries for a page: the rows, the projects they belong to, and the people holding them.
     * Both lookups are for the whole page at once.
     */
    @Transactional(readOnly = true)
    public PageResponse<OverdueTaskResponse> overdueReport(
            UUID workspaceId, ProjectScope scope, UUID assigneeUserId, LocalDate today, Pageable pageable) {

        log.debug("Overdue report for workspace {} as of {}", workspaceId, today);

        Page<TaskReportRow> page = tasks.overdue(workspaceId, scope, assigneeUserId, today, pageable);

        Map<UUID, ProjectSummary> projectsById = projects.summariesOf(
                page.getContent().stream().map(TaskReportRow::projectId).distinct().toList());
        Map<UUID, UserAccount> people = users.findAllByIds(page.getContent().stream()
                .map(TaskReportRow::assigneeUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());

        return PageResponse.of(page, row -> ReportMapper.overdue(row, projectsById, people, today));
    }

    // --- GET /reports/workload ---------------------------------------------

    /**
     * Who is carrying what, paged and sorted within the allowlist.
     *
     * <p>Sorted and paged in this module rather than in SQL, and that is a deliberate exception to the
     * phase's own rule about aggregating in the database. The grouped query returns one row per person
     * holding work, so the collection being ordered is the workspace roster rather than the task
     * table. The alternative would be an {@code ORDER BY} over an aggregate alias chosen by a query
     * parameter, which cannot be written without building SQL from a string.
     *
     * <p>Sorting by name needs the names, so they are resolved before the sort rather than after the
     * page is cut. A page of the wrong people, correctly named, would be a subtle and confident lie.
     */
    @Transactional(readOnly = true)
    public PageResponse<WorkloadResponse> workloadReport(
            UUID workspaceId, ProjectScope scope, ReportPeriod period, Pageable pageable) {

        log.debug(
                "Workload report for workspace {} over {} to {}", workspaceId, period.from(), period.to());

        List<AssigneeTaskCounts> counts = tasks.workload(
                workspaceId, scope, period.today(), period.startInstant(), period.endInstantExclusive());

        Map<UUID, UserAccount> people =
                users.findAllByIds(counts.stream().map(AssigneeTaskCounts::userId).toList());

        List<WorkloadResponse> rows = new ArrayList<>(
                counts.stream().map(row -> ReportMapper.workload(row, people)).toList());
        rows.sort(workloadComparator(pageable.getSort()));

        return PageResponse.of(pageOf(rows, pageable), row -> row);
    }

    // --- GET /reports/trends -----------------------------------------------

    /**
     * Tasks created against tasks completed, one point per bucket.
     *
     * <p>Every bucket in the window is present, including empty ones, so a chart draws a flat line
     * rather than a gap it has to interpolate across. The database returns only the buckets that hold
     * something, which is right for it and wrong for a chart, and {@link TrendBuckets} is where the
     * two meet.
     */
    @Transactional(readOnly = true)
    public List<TrendPointResponse> trends(
            UUID workspaceId,
            ProjectScope scope,
            UUID assigneeUserId,
            ReportPeriod period,
            TrendGranularity granularity) {

        log.debug(
                "Trend report for workspace {} over {} to {} by {}",
                workspaceId,
                period.from(),
                period.to(),
                granularity);

        String zone = period.zone().getId();

        return TrendBuckets.merge(
                period,
                granularity,
                tasks.creationTrend(
                        workspaceId,
                        scope,
                        assigneeUserId,
                        granularity.sqlField(),
                        zone,
                        period.startInstant(),
                        period.endInstantExclusive()),
                tasks.completionTrend(
                        workspaceId,
                        scope,
                        assigneeUserId,
                        granularity.sqlField(),
                        zone,
                        period.startInstant(),
                        period.endInstantExclusive()));
    }

    // --- shared with the dashboards ----------------------------------------

    /** The task counts of a set of projects, keyed by project, in one query. */
    Map<UUID, ProjectTaskCounts> taskCountsOf(List<ProjectSummary> summaries, LocalDate today) {
        List<UUID> ids = summaries.stream().map(ProjectSummary::projectId).toList();

        Map<UUID, ProjectTaskCounts> byProject = new java.util.HashMap<>();
        for (ProjectTaskCounts counts : tasks.countsByProject(ids, today)) {
            byProject.put(counts.projectId(), counts);
        }
        return byProject;
    }

    /** A project with no tasks yet has no row in the grouped result, and reads as zeros. */
    static ProjectTaskCounts countsFor(Map<UUID, ProjectTaskCounts> counts, ProjectSummary project) {
        ProjectTaskCounts found = counts.get(project.projectId());
        return found != null ? found : new ProjectTaskCounts(project.projectId(), 0, 0, 0);
    }

    // --- parsing and paging -------------------------------------------------

    /**
     * The {@code status} filter on the project report.
     *
     * <p>Parsed the same way and refused with the same sentence the project listing uses, so an
     * unknown value fails identically wherever somebody types it. A report that accepted a status the
     * listing rejects would be a second vocabulary for the same field.
     */
    private static List<ProjectStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ProjectStatus> statuses = new ArrayList<>();
        for (String value : raw) {
            try {
                statuses.add(ProjectStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadRequestException("That is not a project status.");
            }
        }
        return List.copyOf(statuses);
    }

    /**
     * The requested ordering of the workload listing.
     *
     * <p>The sort has already been checked against the allowlist, so anything unrecognised here would
     * be a wiring mistake rather than a request. Ties break on the identifier, so paging is stable:
     * without a total order, two people with the same number of open tasks could appear on both page
     * one and page two, or on neither.
     */
    private static Comparator<WorkloadResponse> workloadComparator(Sort sort) {
        Comparator<WorkloadResponse> comparator = null;

        for (Sort.Order order : sort) {
            Comparator<WorkloadResponse> next =
                    switch (order.getProperty()) {
                        case "overdue" -> Comparator.comparingLong(WorkloadResponse::overdue);
                        case "completedInPeriod" -> Comparator.comparingLong(WorkloadResponse::completedInPeriod);
                        case "fullName" -> Comparator.comparing(
                                WorkloadResponse::fullName, Comparator.nullsLast(String::compareToIgnoreCase));
                        default -> Comparator.comparingLong(WorkloadResponse::open);
                    };
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }

        Comparator<WorkloadResponse> stable =
                Comparator.comparing(row -> row.userId().toString());
        return comparator == null ? stable : comparator.thenComparing(stable);
    }

    /** One page of an already ordered list, without asking the database for it again. */
    private static Page<WorkloadResponse> pageOf(List<WorkloadResponse> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
    }
}
