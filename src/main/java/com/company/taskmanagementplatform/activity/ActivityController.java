package com.company.taskmanagementplatform.activity;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.activity.dto.ActivityResponse;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectContext;
import com.company.taskmanagementplatform.tasks.TaskAccessGuard;
import com.company.taskmanagementplatform.workspaces.WorkspaceAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reading the audit trail. Three ways in, and no way to write one.
 *
 * <p>The three differ in who may use them, and the difference is the point. Browsing everything that
 * happened in a workspace is administration and needs {@code activity:read}, which only an
 * administrator holds. One record's history is not: it needs nothing beyond being able to see that
 * record, because a history nobody on the project could read would make the project's own past
 * invisible to the people working on it.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}")
@Tag(name = "Activity", description = "The audit trail: who did what, and when")
class ActivityController {

    private final ActivityService activity;
    private final WorkspaceAccessGuard workspaceGuard;
    private final TaskAccessGuard taskGuard;
    private final ProjectAccessFacade projects;

    ActivityController(
            ActivityService activity,
            WorkspaceAccessGuard workspaceGuard,
            TaskAccessGuard taskGuard,
            ProjectAccessFacade projects) {
        this.activity = activity;
        this.workspaceGuard = workspaceGuard;
        this.taskGuard = taskGuard;
        this.projects = projects;
    }

    @GetMapping("/activity")
    @Operation(summary = "Browse the workspace audit history", description = "Newest first. Administrators only")
    PageResponse<ActivityResponse> forWorkspace(
            @PathVariable UUID workspaceId, @PageableDefault(size = 50) Pageable pageable) {

        workspaceGuard.requirePermission(workspaceId, Permissions.ACTIVITY_READ);
        return activity.forWorkspace(workspaceId, pageable);
    }

    /**
     * One project's history, for anybody who can see the project.
     *
     * <p>A project the caller cannot reach answers 404 rather than an empty page, exactly as its task
     * listing does: an empty page would say the project exists and happens to have no history.
     */
    @GetMapping("/projects/{projectId}/activity")
    @Operation(summary = "One project's history", description = "Newest first")
    PageResponse<ActivityResponse> forProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PageableDefault(size = 50) Pageable pageable) {

        requireReadableProject(workspaceId, projectId);
        return activity.forProject(workspaceId, projectId, pageable);
    }

    @GetMapping("/tasks/{taskId}/activity")
    @Operation(
            summary = "One task's history",
            description = "Includes its subtasks, comments and attachments. Newest first")
    PageResponse<ActivityResponse> forTask(
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @PageableDefault(size = 50) Pageable pageable) {

        return activity.forTask(taskGuard.requireReadableTask(workspaceId, taskId), pageable);
    }

    private void requireReadableProject(UUID workspaceId, UUID projectId) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        if (!granted.contains(Permissions.PROJECT_READ)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Missing permission " + Permissions.PROJECT_READ);
        }

        ProjectContext project = projects
                .contextOf(
                        workspaceId,
                        projectId,
                        CurrentUser.requireId(),
                        granted.contains(Permissions.PROJECT_READ_ANY))
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));

        if (!project.readable()) {
            // Deliberately indistinguishable from something that does not exist.
            throw ResourceNotFoundException.of("Project", projectId);
        }
    }
}
