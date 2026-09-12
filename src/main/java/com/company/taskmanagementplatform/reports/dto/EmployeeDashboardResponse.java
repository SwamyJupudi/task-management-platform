package com.company.taskmanagementplatform.reports.dto;

import java.util.List;

import com.company.taskmanagementplatform.activity.dto.ActivityResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one person sees when they open the product: their work, and nothing else.
 *
 * <p>Every figure here is narrowed to the caller. There is no path, parameter or role that renders
 * one person's dashboard to another, which is why the endpoint takes no user identifier at all rather
 * than taking one and checking it.
 *
 * <p>Assembled in one read-only transaction, so the panels are consistent with each other. A task
 * finished halfway through the request cannot appear as open in one figure and done in the next.
 *
 * @param myProjects the projects in the caller's reach, most recently touched first and capped, with
 *     their progress. A panel rather than a listing: the project report is where somebody goes to
 *     page through all of them
 * @param myTaskCounts the caller's own tasks by status and by priority
 * @param overdueCount how many of the caller's tasks are late. A number rather than a list, because
 *     the overdue report is the list
 * @param upcomingDeadlines the caller's tasks due inside the lead window, soonest first
 * @param recentActivity what the caller themselves did, newest first. Not what happened around them:
 *     browsing the workspace's audit trail is an administrator's permission and this is not a way
 *     around it
 * @param myOpenSubtasks checklist items assigned to the caller and not finished. Work they hold that
 *     the task counts do not show
 */
@Schema(name = "EmployeeDashboard", description = "One person's own work")
public record EmployeeDashboardResponse(
        List<ProjectProgressResponse> myProjects,
        DistributionResponse myTaskCounts,
        @Schema(example = "3") long overdueCount,
        List<UpcomingDeadlineResponse> upcomingDeadlines,
        List<ActivityResponse> recentActivity,
        @Schema(example = "5") long myOpenSubtasks) {}
