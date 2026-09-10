package com.company.taskmanagementplatform.projects;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.dto.ChangeProjectStatusRequest;
import com.company.taskmanagementplatform.projects.dto.CreateProjectRequest;
import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.projects.dto.UpdateProjectRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The projects of one workspace.
 *
 * <p>The workspace comes from the path and never from a header, so it can never be inherited from
 * ambient state. Every method starts at {@link ProjectAccessGuard}, which answers 404 for a workspace
 * or a project the caller cannot see, 403 for one they can see but may not act on, and 409 for a
 * workspace that has been archived.
 *
 * <p>A listing returns what the caller may see rather than everything in the workspace. That is not
 * a filter a client applies; the guard decides it and the query enforces it.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/projects")
@Tag(name = "Projects", description = "Projects within a workspace, their lifecycle and their members")
class ProjectController {

    private final ProjectService projects;
    private final ProjectAccessGuard guard;

    ProjectController(ProjectService projects, ProjectAccessGuard guard) {
        this.projects = projects;
        this.guard = guard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a project", description = "Always starts in PLANNING; a named owner joins it")
    ProjectResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody CreateProjectRequest request) {
        guard.requireCreateAccess(workspaceId);
        return projects.create(workspaceId, request, CurrentUser.requireId());
    }

    /**
     * Lists the projects the caller may see, filtered and sorted.
     *
     * <p>Sorting is restricted to an allowlist. Passing the request straight through would let a
     * query parameter probe the shape of the entity and order by columns with no index behind them.
     */
    @GetMapping
    @Operation(
            summary = "List projects",
            description = "Returns assigned projects unless the caller may read every project")
    PageResponse<ProjectResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String label,
            @PageableDefault(size = 20) Pageable pageable) {

        ProjectVisibility visibility = guard.requireListAccess(workspaceId);

        ProjectFilter filter = new ProjectFilter(
                status == null ? null : ProjectService.parseStatus(status),
                priority == null ? null : ProjectService.parsePriority(priority),
                teamId,
                ownerUserId,
                q,
                label);

        return PageResponse.of(projects.list(workspaceId, filter, visibility, pageable), project -> project);
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Fetch one project")
    ProjectResponse get(@PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        guard.requireReadableProject(workspaceId, projectId);
        return projects.describe(workspaceId, projectId);
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Edit a project", description = "Omitted fields are left alone; labels replace the set")
    ProjectResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        guard.requireChangeableProject(workspaceId, projectId, Permissions.PROJECT_UPDATE);
        return projects.update(workspaceId, projectId, request, CurrentUser.requireId());
    }

    /**
     * Moves a project through its lifecycle.
     *
     * <p>Its own endpoint rather than a field on the edit, because a transition is checked against
     * the state machine and a rejected one is a 409 rather than a validation error.
     */
    @PostMapping("/{projectId}/status")
    @Operation(summary = "Change project status", description = "Refuses a move the lifecycle does not allow")
    ProjectResponse changeStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody ChangeProjectStatusRequest request) {
        guard.requireChangeableProject(workspaceId, projectId, Permissions.PROJECT_UPDATE);
        return projects.changeStatus(workspaceId, projectId, request.status(), CurrentUser.requireId());
    }

    /**
     * Removes a project and frees its key and name.
     *
     * <p>{@code project:delete} is held by the workspace administrator and not by a team lead, so
     * this is the one project operation a lead cannot perform on a project they lead.
     */
    @DeleteMapping("/{projectId}")
    @Operation(summary = "Remove a project", description = "Soft delete; the key and name become available again")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        guard.requireChangeableProject(workspaceId, projectId, Permissions.PROJECT_DELETE);
        projects.delete(workspaceId, projectId, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }
}
