package com.company.taskmanagementplatform.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.company.taskmanagementplatform.projects.ProjectSummary;
import com.company.taskmanagementplatform.reports.dto.CountByKeyResponse;
import com.company.taskmanagementplatform.reports.dto.DistributionResponse;
import com.company.taskmanagementplatform.reports.dto.OverdueTaskResponse;
import com.company.taskmanagementplatform.reports.dto.ProjectProgressResponse;
import com.company.taskmanagementplatform.reports.dto.UpcomingDeadlineResponse;
import com.company.taskmanagementplatform.reports.dto.WorkloadResponse;
import com.company.taskmanagementplatform.tasks.AssigneeTaskCounts;
import com.company.taskmanagementplatform.tasks.ProjectTaskCounts;
import com.company.taskmanagementplatform.tasks.TaskPriority;
import com.company.taskmanagementplatform.tasks.TaskPriorityCount;
import com.company.taskmanagementplatform.tasks.TaskReportRow;
import com.company.taskmanagementplatform.tasks.TaskStatus;
import com.company.taskmanagementplatform.tasks.TaskStatusCount;
import com.company.taskmanagementplatform.users.UserAccount;

/**
 * Turns what the owning modules answered into what the API returns.
 *
 * <p>Two jobs, both of which exist because a facade deliberately answers with the smallest true thing
 * rather than with a rendered row.
 *
 * <p><strong>Filling the gaps.</strong> A grouped query returns no row for a status nothing holds,
 * which is correct and is the wrong shape for a chart: a missing column makes a client know the whole
 * enum to draw the axis. Every breakdown produced here carries every value of its enum, in the order
 * the enum declares, which for statuses is the order of the board.
 *
 * <p><strong>Resolving identifiers to names.</strong> A page of rows carries project and user
 * identifiers; people read keys and names. The lookups are passed in already done, once for a whole
 * page, because a lookup per row is exactly how a listing becomes N+1 without anybody noticing until
 * production.
 *
 * <p>A name that cannot be resolved leaves a null rather than dropping the row. Somebody whose account
 * was removed still did the work, and a report that quietly omitted their tasks would not add up
 * against the counts beside it.
 */
final class ReportMapper {

    private ReportMapper() {}

    // --- breakdowns ---------------------------------------------------------

    static DistributionResponse distribution(List<TaskStatusCount> byStatus, List<TaskPriorityCount> byPriority) {
        Map<TaskStatus, Long> statuses = new EnumMap<>(TaskStatus.class);
        for (TaskStatusCount count : byStatus) {
            statuses.merge(count.status(), count.count(), Long::sum);
        }

        Map<TaskPriority, Long> priorities = new EnumMap<>(TaskPriority.class);
        for (TaskPriorityCount count : byPriority) {
            priorities.merge(count.priority(), count.count(), Long::sum);
        }

        List<CountByKeyResponse> statusColumns = new ArrayList<>();
        long total = 0;
        for (TaskStatus status : TaskStatus.values()) {
            long count = statuses.getOrDefault(status, 0L);
            total += count;
            statusColumns.add(column(status.name(), count));
        }

        List<CountByKeyResponse> priorityColumns = new ArrayList<>();
        for (TaskPriority priority : TaskPriority.values()) {
            priorityColumns.add(column(priority.name(), priorities.getOrDefault(priority, 0L)));
        }

        return new DistributionResponse(List.copyOf(statusColumns), List.copyOf(priorityColumns), total);
    }

    /**
     * An enum constant as a person reads it: {@code IN_PROGRESS} becomes "In progress".
     *
     * <p>Composed here rather than stored or translated. The platform has no message catalog yet, and
     * inventing one for four words would be a feature rather than a label. The machine value travels
     * beside it, so a client with its own translations ignores this and uses that.
     */
    private static CountByKeyResponse column(String key, long count) {
        String words = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        String label = Character.toUpperCase(words.charAt(0)) + words.substring(1);
        return new CountByKeyResponse(key, label, count);
    }

    // --- projects -----------------------------------------------------------

    static ProjectProgressResponse project(ProjectSummary project, ProjectTaskCounts counts) {
        return new ProjectProgressResponse(
                project.projectId(),
                project.key(),
                project.name(),
                project.status().name(),
                project.progress(),
                counts.total(),
                counts.done(),
                counts.overdue(),
                project.teamId(),
                project.ownerUserId());
    }

    // --- task rows ----------------------------------------------------------

    static OverdueTaskResponse overdue(
            TaskReportRow row, Map<UUID, ProjectSummary> projects, Map<UUID, UserAccount> people, LocalDate today) {

        ProjectSummary project = projects.get(row.projectId());
        UserAccount assignee = row.assigneeUserId() == null ? null : people.get(row.assigneeUserId());

        return new OverdueTaskResponse(
                row.taskId(),
                taskKey(project, row.taskNumber()),
                row.title(),
                row.projectId(),
                project == null ? null : project.name(),
                row.assigneeUserId(),
                assignee == null ? null : assignee.fullName(),
                row.status().name(),
                row.priority().name(),
                row.dueDate(),
                ReportDefinitions.daysOverdue(row.dueDate(), today));
    }

    static UpcomingDeadlineResponse upcoming(
            TaskReportRow row, Map<UUID, ProjectSummary> projects, LocalDate today) {

        return new UpcomingDeadlineResponse(
                row.taskId(),
                taskKey(projects.get(row.projectId()), row.taskNumber()),
                row.title(),
                row.projectId(),
                row.priority().name(),
                row.dueDate(),
                ReportDefinitions.daysRemaining(today, row.dueDate()));
    }

    /**
     * The rendered task identifier, {@code PROJ-12}.
     *
     * <p>Composed from the project key and the number rather than stored, exactly as the tasks module
     * composes it. A key written into a second place would still say the old one after a project was
     * renamed.
     */
    private static String taskKey(ProjectSummary project, int taskNumber) {
        return project == null ? null : project.key() + "-" + taskNumber;
    }

    // --- people -------------------------------------------------------------

    static WorkloadResponse workload(AssigneeTaskCounts counts, Map<UUID, UserAccount> people) {
        UserAccount person = people.get(counts.userId());
        return new WorkloadResponse(
                counts.userId(),
                person == null ? null : person.email(),
                person == null ? null : person.fullName(),
                counts.open(),
                counts.inProgress(),
                counts.overdue(),
                counts.completedInPeriod(),
                counts.estimatedMinutes(),
                counts.actualMinutes());
    }

    /** Somebody on a team who is carrying nothing, which is a fact about the team rather than a gap. */
    static WorkloadResponse emptyWorkload(UUID userId, Map<UUID, UserAccount> people) {
        return workload(new AssigneeTaskCounts(userId, 0, 0, 0, 0, 0, 0), people);
    }
}
