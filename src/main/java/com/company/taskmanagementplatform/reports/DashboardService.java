package com.company.taskmanagementplatform.reports;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.activity.ActivityService;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectAnalyticsFacade;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.projects.ProjectStatus;
import com.company.taskmanagementplatform.projects.ProjectStatusCount;
import com.company.taskmanagementplatform.projects.ProjectSummary;
import com.company.taskmanagementplatform.projects.TeamProjectStats;
import com.company.taskmanagementplatform.reports.dto.AdminDashboardResponse;
import com.company.taskmanagementplatform.reports.dto.DistributionResponse;
import com.company.taskmanagementplatform.reports.dto.EmployeeDashboardResponse;
import com.company.taskmanagementplatform.reports.dto.ProjectProgressResponse;
import com.company.taskmanagementplatform.reports.dto.TeamDashboardResponse;
import com.company.taskmanagementplatform.reports.dto.TeamPerformanceResponse;
import com.company.taskmanagementplatform.reports.dto.UpcomingDeadlineResponse;
import com.company.taskmanagementplatform.reports.dto.WorkloadResponse;
import com.company.taskmanagementplatform.subtasks.SubtaskAnalyticsFacade;
import com.company.taskmanagementplatform.tasks.AssigneeTaskCounts;
import com.company.taskmanagementplatform.tasks.ProjectTaskCounts;
import com.company.taskmanagementplatform.tasks.TaskAnalyticsFacade;
import com.company.taskmanagementplatform.tasks.TaskAnalyticsFilter;
import com.company.taskmanagementplatform.tasks.TaskCounters;
import com.company.taskmanagementplatform.tasks.TaskReportRow;
import com.company.taskmanagementplatform.teams.TeamAnalyticsFacade;
import com.company.taskmanagementplatform.teams.TeamSummary;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * The three dashboards, each assembled in one read-only transaction.
 *
 * <p>A dashboard is a set of figures that have to agree with each other. Read in one transaction they
 * do: a task finished halfway through the request cannot be counted as open in one panel and as done
 * in the next. That is the whole reason these are composed here rather than by a client making six
 * requests and drawing whatever came back.
 *
 * <p><strong>Every panel is bounded.</strong> Nothing here returns a collection whose size grows with
 * the workspace. The project and team panels are capped, the deadline panel is capped, the activity
 * panel is a page, and everything else is a number. Somebody who wants the long version goes to the
 * report, which pages.
 *
 * <p><strong>Every panel is also a fixed number of queries.</strong> Names and counts are resolved for
 * a whole panel at once, never per row. A dashboard is the easiest place in a platform to write an
 * N+1: the loops look harmless and the rows look small, and it is only ever noticed in production.
 * {@code ReportScaleIT} is what holds that true.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    /** Projects on a dashboard panel. Ten is a glance; the project report is the list. */
    private static final int MY_PROJECTS = 10;

    /** The same for the administrator, who has more to look at. */
    private static final int WORKSPACE_PROJECTS = 20;

    /** Teams on the performance panel. */
    private static final int TEAMS = 20;

    /** Deadlines on the upcoming panel, inside the lead window. */
    private static final int UPCOMING = 20;

    /** Rows of the caller's own history. Enough to recognise where they left off. */
    private static final int RECENT_ACTIVITY = 10;

    /** Most recently touched first, which is the order somebody actually thinks about their work in. */
    private static final Sort RECENTLY_TOUCHED = Sort.by(Sort.Direction.DESC, "updatedAt");

    private final ReportService reports;
    private final TaskAnalyticsFacade tasks;
    private final SubtaskAnalyticsFacade subtasks;
    private final ProjectAnalyticsFacade projectAnalytics;
    private final ProjectAccessFacade projects;
    private final TeamAnalyticsFacade teams;
    private final MembershipService memberships;
    private final ActivityService activity;
    private final UserAccountService users;
    private final ReportProperties properties;

    DashboardService(
            ReportService reports,
            TaskAnalyticsFacade tasks,
            SubtaskAnalyticsFacade subtasks,
            ProjectAnalyticsFacade projectAnalytics,
            ProjectAccessFacade projects,
            TeamAnalyticsFacade teams,
            MembershipService memberships,
            ActivityService activity,
            UserAccountService users,
            ReportProperties properties) {
        this.reports = reports;
        this.tasks = tasks;
        this.subtasks = subtasks;
        this.projectAnalytics = projectAnalytics;
        this.projects = projects;
        this.teams = teams;
        this.memberships = memberships;
        this.activity = activity;
        this.users = users;
        this.properties = properties;
    }

    // --- GET /dashboard/me --------------------------------------------------

    /**
     * One person's own dashboard.
     *
     * <p>Narrowed to them in two different ways, and the difference matters. The project panel is
     * narrowed by <em>scope</em>: the projects they can reach, whether or not they hold work in them.
     * Everything else is narrowed by <em>assignment</em>: tasks with their name on. A project they can
     * see but hold nothing in belongs on the first list and in none of the counts.
     */
    @Transactional(readOnly = true)
    public EmployeeDashboardResponse forEmployee(UUID workspaceId, UUID userId, ProjectScope scope, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        log.debug("Employee dashboard for workspace {} as of {}", workspaceId, today);

        List<ProjectProgressResponse> myProjects = projectPanel(workspaceId, scope, today, MY_PROJECTS);

        TaskAnalyticsFilter mine = TaskAnalyticsFilter.assignedTo(userId);
        DistributionResponse myTaskCounts = ReportMapper.distribution(
                tasks.countsByStatus(workspaceId, scope, mine), tasks.countsByPriority(workspaceId, scope, mine));

        TaskCounters counters = tasks.counters(workspaceId, scope, userId, today);

        List<UpcomingDeadlineResponse> upcoming = upcomingPanel(workspaceId, userId, today);

        return new EmployeeDashboardResponse(
                myProjects,
                myTaskCounts,
                counters.overdue(),
                upcoming,
                activity.forActor(workspaceId, userId, PageRequest.of(0, RECENT_ACTIVITY))
                        .content(),
                subtasks.openCountFor(workspaceId, userId));
    }

    /**
     * The caller's next deadlines, inside the lead window.
     *
     * <p>The window is {@code app.reports.upcoming-lead-days}, a week, and deliberately not the
     * deadline notification's two days. A notification is a nudge about something imminent; a
     * dashboard is a week's planning, and the two are different questions that happen to read the same
     * column.
     */
    private List<UpcomingDeadlineResponse> upcomingPanel(UUID workspaceId, UUID userId, LocalDate today) {
        LocalDate until = ReportDefinitions.upcomingWindowEnd(today, properties.upcomingLeadDays());

        List<TaskReportRow> rows = tasks.upcomingFor(workspaceId, userId, today, until, UPCOMING);

        Map<UUID, ProjectSummary> projectsById = projectAnalytics.summariesOf(
                rows.stream().map(TaskReportRow::projectId).distinct().toList());

        return rows.stream()
                .map(row -> ReportMapper.upcoming(row, projectsById, today))
                .toList();
    }

    // --- GET /dashboard/workspace -------------------------------------------

    /**
     * The administrator's dashboard, over the whole workspace.
     *
     * <p>Computed over {@link ProjectScope#everything()} rather than over the caller's reach, because
     * the endpoint already refused anybody without {@code project:read_any}. Passing a narrowed scope
     * here would produce a workspace-wide figure that quietly was not one.
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse forWorkspace(UUID workspaceId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        log.debug("Workspace dashboard for workspace {} as of {}", workspaceId, today);

        ProjectScope everything = ProjectScope.everything();

        Map<ProjectStatus, Long> projectsByStatus = new java.util.EnumMap<>(ProjectStatus.class);
        for (ProjectStatusCount count : projectAnalytics.countsByStatus(workspaceId, everything)) {
            projectsByStatus.merge(count.status(), count.count(), Long::sum);
        }

        TaskCounters counters = tasks.counters(workspaceId, everything, null, today);

        TaskAnalyticsFilter all = TaskAnalyticsFilter.none();
        DistributionResponse distribution = ReportMapper.distribution(
                tasks.countsByStatus(workspaceId, everything, all), tasks.countsByPriority(workspaceId, everything, all));

        return new AdminDashboardResponse(
                memberships.countMembers(workspaceId),
                memberships.countMembersByRole(workspaceId),
                teams.countTeams(workspaceId),
                projectsByStatus.getOrDefault(ProjectStatus.ACTIVE, 0L),
                projectsByStatus.getOrDefault(ProjectStatus.COMPLETED, 0L),
                counters.open(),
                counters.overdue(),
                distribution,
                projectPanel(workspaceId, everything, today, WORKSPACE_PROJECTS),
                teamPanel(workspaceId, everything, today));
    }

    /**
     * One line per team, for the performance panel.
     *
     * <p>Four queries for the whole panel however many teams it holds: the teams with their roster
     * sizes, the projects grouped by team, the project statistics grouped by team, and the task counts
     * of every project on the panel at once. The alternative shape, a loop asking about one team at a
     * time, is the N+1 this phase's facades exist to make awkward to write.
     */
    private List<TeamPerformanceResponse> teamPanel(UUID workspaceId, ProjectScope scope, LocalDate today) {
        List<TeamSummary> summaries = teams.activeTeams(workspaceId, TEAMS);
        if (summaries.isEmpty()) {
            return List.of();
        }

        List<UUID> teamIds = summaries.stream().map(TeamSummary::teamId).toList();
        Map<UUID, List<UUID>> projectsByTeam = projectAnalytics.projectIdsByTeam(workspaceId, teamIds);

        Map<UUID, TeamProjectStats> statsByTeam = new java.util.HashMap<>();
        for (TeamProjectStats stats : projectAnalytics.statsByTeam(workspaceId, scope)) {
            statsByTeam.put(stats.teamId(), stats);
        }

        Set<UUID> everyProject = new LinkedHashSet<>();
        projectsByTeam.values().forEach(everyProject::addAll);

        Map<UUID, ProjectTaskCounts> countsByProject = new java.util.HashMap<>();
        for (ProjectTaskCounts counts : tasks.countsByProject(everyProject, today)) {
            countsByProject.put(counts.projectId(), counts);
        }

        List<TeamPerformanceResponse> panel = new ArrayList<>();
        for (TeamSummary team : summaries) {
            List<UUID> teamProjects = projectsByTeam.getOrDefault(team.teamId(), List.of());
            TeamProjectStats stats = statsByTeam.get(team.teamId());

            long total = 0;
            long done = 0;
            long overdue = 0;
            for (UUID projectId : teamProjects) {
                ProjectTaskCounts counts = countsByProject.get(projectId);
                if (counts != null) {
                    total += counts.total();
                    done += counts.done();
                    overdue += counts.overdue();
                }
            }

            panel.add(new TeamPerformanceResponse(
                    team.teamId(),
                    team.name(),
                    team.leadUserId(),
                    team.memberCount(),
                    stats == null ? 0 : stats.projectCount(),
                    total - done,
                    overdue,
                    done,
                    stats == null ? 0 : stats.averageProgress()));
        }
        return List.copyOf(panel);
    }

    // --- GET /teams/{teamId}/dashboard --------------------------------------

    /**
     * One team's dashboard, computed over the team's projects rather than over the viewer's reach.
     *
     * <p>The one place in this phase where the figures are not narrowed per caller, and the guard is
     * narrower to compensate. The reasoning is on {@code TeamDashboardResponse}: two people opening the
     * same team dashboard should see the same numbers, or one of them should not be able to open it.
     *
     * <p>A team with no projects produces zeros rather than 404. {@link ProjectScope#of} over an empty
     * list reaches nothing, and every facade answers a scope that reaches nothing with an empty result
     * rather than with everything, which is the distinction that keeps this from being a disclosure.
     */
    @Transactional(readOnly = true)
    public TeamDashboardResponse forTeam(UUID workspaceId, TeamSummary team, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        log.debug("Team dashboard for workspace {} as of {}", workspaceId, today);

        List<UUID> teamProjects = projects.projectIdsOfTeam(workspaceId, team.teamId());
        ProjectScope teamScope = ProjectScope.of(teamProjects);

        TaskCounters counters = tasks.counters(workspaceId, teamScope, null, today);

        TaskAnalyticsFilter all = TaskAnalyticsFilter.none();
        DistributionResponse distribution = ReportMapper.distribution(
                tasks.countsByStatus(workspaceId, teamScope, all), tasks.countsByPriority(workspaceId, teamScope, all));

        int averageProgress = projectAnalytics.statsByTeam(workspaceId, teamScope).stream()
                .filter(stats -> stats.teamId().equals(team.teamId()))
                .map(TeamProjectStats::averageProgress)
                .findFirst()
                .orElse(0);

        ReportPeriod period = ReportPeriod.resolve(null, null, zone, properties);

        return new TeamDashboardResponse(
                team.teamId(),
                team.name(),
                team.leadUserId(),
                team.memberCount(),
                teamProjects.size(),
                counters.open(),
                counters.overdue(),
                counters.done(),
                averageProgress,
                workloadPanel(workspaceId, team, teamScope, period),
                distribution);
    }

    /**
     * One row per person the team's work touches.
     *
     * <p>Every member of the team appears, including those carrying nothing: somebody with an empty
     * desk is a fact about a team rather than a row to leave out. So does anybody outside the team
     * holding work in its projects, because a borrowed hand's tasks are already inside the team's
     * totals and a panel whose rows did not add up to the number above them would be worse than one
     * with an extra row.
     */
    private List<WorkloadResponse> workloadPanel(
            UUID workspaceId, TeamSummary team, ProjectScope teamScope, ReportPeriod period) {

        List<AssigneeTaskCounts> counts = tasks.workload(
                workspaceId, teamScope, period.today(), period.startInstant(), period.endInstantExclusive());

        Set<UUID> everybody = new LinkedHashSet<>(teams.memberUserIds(team.teamId()));
        counts.forEach(row -> everybody.add(row.userId()));

        Map<UUID, UserAccount> people = users.findAllByIds(everybody);
        Map<UUID, AssigneeTaskCounts> byUser = new java.util.HashMap<>();
        counts.forEach(row -> byUser.put(row.userId(), row));

        List<WorkloadResponse> panel = new ArrayList<>();
        for (UUID userId : everybody) {
            AssigneeTaskCounts row = byUser.get(userId);
            panel.add(row == null
                    ? ReportMapper.emptyWorkload(userId, people)
                    : ReportMapper.workload(row, people));
        }
        return List.copyOf(panel);
    }

    // --- shared -------------------------------------------------------------

    /** A capped panel of projects with their progress and task counts, in two queries. */
    private List<ProjectProgressResponse> projectPanel(
            UUID workspaceId, ProjectScope scope, LocalDate today, int limit) {

        Page<ProjectSummary> page = projectAnalytics.page(
                workspaceId, scope, List.of(), null, null, PageRequest.of(0, limit, RECENTLY_TOUCHED));

        Map<UUID, ProjectTaskCounts> counts = reports.taskCountsOf(page.getContent(), today);

        return page.getContent().stream()
                .map(project -> ReportMapper.project(project, ReportService.countsFor(counts, project)))
                .toList();
    }
}
