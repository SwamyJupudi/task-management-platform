package com.company.taskmanagementplatform.reports;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.reports.dto.DistributionResponse;
import com.company.taskmanagementplatform.reports.dto.OverdueTaskResponse;
import com.company.taskmanagementplatform.reports.dto.ProjectProgressResponse;
import com.company.taskmanagementplatform.reports.dto.TrendPointResponse;
import com.company.taskmanagementplatform.reports.dto.WorkloadResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The five reports the requirements name, each narrowed to what the caller may see.
 *
 * <p>Every one of them computes its figure over the caller's project read scope, resolved by {@link
 * ReportAccessGuard} and applied inside the aggregate query rather than to its result. That is what
 * makes a report incapable of being a way past the scope layer: the filters a client sends are joined
 * to the scope predicate with AND, so narrowing by project, team or person can only ever return rows
 * they could already have listed.
 *
 * <p>A caller who reaches no project gets zeros, empty pages and 200. They belong to the workspace;
 * there is simply nothing of theirs in it. Refusing them would confuse having no work with having no
 * business asking.
 *
 * <p>Every window is resolved once per request in the workspace's own timezone, by {@link
 * ReportPeriod}. No endpoint here decides for itself what an absent pair of dates means, what an
 * inverted one means, or how wide is too wide, because three answers repeated five times is three
 * answers that drift.
 *
 * <p>Nothing here writes, so nothing here answers 409 and nothing asks whether the workspace is
 * archived. A frozen workspace is exactly the one somebody wants to read the numbers of.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/reports")
@Tag(name = "Reports", description = "Projects, tasks, workload and productivity, filtered and paged")
class ReportController {

    private static final Sort PROGRESS_DESCENDING = Sort.by(Sort.Direction.DESC, "progress");

    /** The longest overdue first, which is the row somebody actually has to do something about. */
    private static final Sort LONGEST_OVERDUE_FIRST = Sort.by(Sort.Direction.ASC, "dueDate");

    private static final Sort BUSIEST_FIRST = Sort.by(Sort.Direction.DESC, "open");

    private final ReportService reports;
    private final ReportAccessGuard guard;

    ReportController(ReportService reports, ReportAccessGuard guard) {
        this.reports = reports;
        this.guard = guard;
    }

    /**
     * Project completion and progress.
     *
     * <p>The {@code teamId} filter goes through the same narrowing every other report's does, so a
     * team the caller cannot see answers 404 rather than an empty page. An empty page would say the
     * team exists and happens to run nothing, which is a different statement and an untrue one.
     */
    @GetMapping("/projects")
    @Operation(
            summary = "Project completion and progress",
            description = "Each project's derived progress beside its task counts. Sorted by progress by default")
    PageResponse<ProjectProgressResponse> projects(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID ownerUserId,
            @PageableDefault(size = 20) Pageable pageable) {

        ProjectScope scope = guard.requireProjectReadAccess(workspaceId);
        ZoneId zone = reports.zoneOf(workspaceId);

        return reports.projectReport(
                workspaceId,
                guard.narrow(workspaceId, scope, null, teamId),
                status,
                teamId,
                ownerUserId,
                LocalDate.now(zone),
                ReportSorts.paged(
                        pageable,
                        ReportSorts.PROJECTS,
                        PROGRESS_DESCENDING,
                        reports.properties().maxPageSize()));
    }

    /**
     * Task completion and status distribution.
     *
     * <p>The optional window is over when a task was <em>created</em>. "How is the work we took on
     * last month spread" is the question a status breakdown answers; when work was finished is the
     * trend report's subject rather than a filter here.
     */
    @GetMapping("/tasks/distribution")
    @Operation(
            summary = "Task distribution",
            description = "Counts by status and by priority, with every column present including empty ones")
    DistributionResponse distribution(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        ProjectScope scope = guard.requireReadAccess(workspaceId);
        ZoneId zone = reports.zoneOf(workspaceId);

        boolean windowed = from != null || to != null;
        ReportPeriod period = ReportPeriod.resolve(from, to, zone, reports.properties());

        return reports.distribution(
                workspaceId, guard.narrow(workspaceId, scope, projectId, teamId), assigneeUserId, period, windowed);
    }

    /** Everything late. */
    @GetMapping("/tasks/overdue")
    @Operation(
            summary = "Overdue tasks",
            description = "Open work past its due date, measured in the workspace timezone. Longest overdue first")
    PageResponse<OverdueTaskResponse> overdue(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID assigneeUserId,
            @PageableDefault(size = 20) Pageable pageable) {

        ProjectScope scope = guard.requireReadAccess(workspaceId);
        ZoneId zone = reports.zoneOf(workspaceId);

        return reports.overdueReport(
                workspaceId,
                guard.narrow(workspaceId, scope, projectId, teamId),
                assigneeUserId,
                LocalDate.now(zone),
                ReportSorts.paged(
                        pageable,
                        ReportSorts.OVERDUE,
                        LONGEST_OVERDUE_FIRST,
                        reports.properties().maxPageSize()));
    }

    /** Team and employee workload. */
    @GetMapping("/workload")
    @Operation(
            summary = "Workload per person",
            description = "Open, in progress, overdue and completed in the period, with effort over open work")
    PageResponse<WorkloadResponse> workload(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {

        ProjectScope scope = guard.requireReadAccess(workspaceId);
        ZoneId zone = reports.zoneOf(workspaceId);

        return reports.workloadReport(
                workspaceId,
                guard.narrow(workspaceId, scope, projectId, teamId),
                ReportPeriod.resolve(from, to, zone, reports.properties()),
                ReportSorts.paged(
                        pageable, ReportSorts.WORKLOAD, BUSIEST_FIRST, reports.properties().maxPageSize()));
    }

    /**
     * Productivity trends.
     *
     * <p>Not paged, because it is a chart rather than a listing. It is bounded instead by the window,
     * which {@code app.reports.max-period-days} caps, so the longest answer this can produce is a
     * year of daily points.
     */
    @GetMapping("/trends")
    @Operation(
            summary = "Productivity trends",
            description = "Tasks created against tasks completed per bucket, with empty buckets present")
    List<TrendPointResponse> trends(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String granularity) {

        ProjectScope scope = guard.requireReadAccess(workspaceId);
        ZoneId zone = reports.zoneOf(workspaceId);

        ReportPeriod period = ReportPeriod.resolve(from, to, zone, reports.properties());

        TrendGranularity resolved = TrendGranularity.parse(granularity);
        if (resolved == null) {
            resolved = TrendGranularity.defaultFor(period.from(), period.to());
        }

        return reports.trends(
                workspaceId,
                guard.narrow(workspaceId, scope, projectId, teamId),
                assigneeUserId,
                period,
                resolved);
    }
}
