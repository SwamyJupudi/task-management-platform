package com.company.taskmanagementplatform.projects;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.dto.AddProjectMemberRequest;
import com.company.taskmanagementplatform.projects.dto.AssignProjectOwnerRequest;
import com.company.taskmanagementplatform.projects.dto.ProjectMemberResponse;
import com.company.taskmanagementplatform.projects.dto.ProjectResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The roster of one project, and who owns it.
 *
 * <p>Reading needs {@code project:read} and the project has to be one the caller may see. Changing
 * needs {@code project:manage_members}, and for anybody without {@code project:manage_any} the guard
 * narrows that to the projects they own or whose team they lead.
 *
 * <p>The owner is a subresource with its own two verbs rather than a field on the project, because
 * naming one also puts that person on the project. That is worth being explicit about instead of
 * happening as a side effect of a general edit.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/projects/{projectId}")
@Tag(name = "Project members", description = "Project membership and ownership")
class ProjectMemberController {

    private final ProjectMembershipService memberships;
    private final ProjectAccessGuard guard;

    ProjectMemberController(ProjectMembershipService memberships, ProjectAccessGuard guard) {
        this.memberships = memberships;
        this.guard = guard;
    }

    @GetMapping("/members")
    @Operation(summary = "List the members of a project")
    PageResponse<ProjectMemberResponse> listMembers(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PageableDefault(size = 20) Pageable pageable) {
        guard.requireReadableProject(workspaceId, projectId);
        return PageResponse.of(memberships.listMembers(workspaceId, projectId, pageable), member -> member);
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a workspace member to a project")
    ProjectMemberResponse addMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectMemberRequest request) {
        guard.requireChangeableProject(workspaceId, projectId, Permissions.PROJECT_MANAGE_MEMBERS);
        return memberships.addMember(workspaceId, projectId, request.userId(), CurrentUser.requireId());
    }

    @DeleteMapping("/members/{userId}")
    @Operation(summary = "Remove somebody from a project", description = "Refuses if they own it")
    ResponseEntity<Void> removeMember(
            @PathVariable UUID workspaceId, @PathVariable UUID projectId, @PathVariable UUID userId) {
        guard.requireChangeableProject(workspaceId, projectId, Permissions.PROJECT_MANAGE_MEMBERS);
        memberships.removeMember(workspaceId, projectId, userId, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/owner")
    @Operation(summary = "Assign the project owner", description = "Adds them to the project if they are not on it")
    ProjectResponse assignOwner(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody AssignProjectOwnerRequest request) {
        guard.requireChangeableProject(workspaceId, projectId, Permissions.PROJECT_MANAGE_MEMBERS);
        return memberships.assignOwner(workspaceId, projectId, request.userId(), CurrentUser.requireId());
    }

    @DeleteMapping("/owner")
    @Operation(summary = "Leave the project without an owner", description = "They stay a member of it")
    ProjectResponse clearOwner(@PathVariable UUID workspaceId, @PathVariable UUID projectId) {
        guard.requireChangeableProject(workspaceId, projectId, Permissions.PROJECT_MANAGE_MEMBERS);
        return memberships.clearOwner(workspaceId, projectId, CurrentUser.requireId());
    }
}
