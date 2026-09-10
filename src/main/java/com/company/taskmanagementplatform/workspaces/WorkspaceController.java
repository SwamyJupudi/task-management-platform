package com.company.taskmanagementplatform.workspaces;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.workspaces.dto.CreateWorkspaceRequest;
import com.company.taskmanagementplatform.workspaces.dto.UpdateWorkspaceRequest;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Creating and listing workspaces, and the settings and lifecycle of one.
 *
 * <p>Ordinary members do not list workspaces here. They receive the ones they belong to from the
 * endpoint that describes their session, which is a different question with a different answer:
 * "which workspaces exist" against "which are mine".
 *
 * <p>The two platform-wide operations are annotated; everything addressed to one workspace goes
 * through the guard instead, because a workspace the caller has nothing to do with has to answer as
 * missing rather than as forbidden and an annotation can only permit or deny.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces")
@Tag(name = "Workspaces", description = "Workspace creation, settings, and lifecycle")
class WorkspaceController {

    private final WorkspaceProvisioningService provisioning;
    private final WorkspaceLifecycleService lifecycle;
    private final WorkspaceAccessGuard guard;

    WorkspaceController(
            WorkspaceProvisioningService provisioning,
            WorkspaceLifecycleService lifecycle,
            WorkspaceAccessGuard guard) {
        this.provisioning = provisioning;
        this.lifecycle = lifecycle;
        this.guard = guard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.onPlatform('workspace:create')")
    @Operation(summary = "Create a workspace", description = "Seeds its roles in the same transaction")
    WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return provisioning.create(request.name(), request.slug(), CurrentUser.requireId());
    }

    @GetMapping
    @PreAuthorize("@perm.onPlatform('workspace:read')")
    @Operation(summary = "List every workspace")
    PageResponse<WorkspaceResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(provisioning.listAll(pageable), workspace -> workspace);
    }

    /**
     * One workspace, visible to its members and to a platform administrator.
     *
     * <p>Guarded rather than annotated, because a workspace the caller has nothing to do with has to
     * answer as missing rather than as forbidden.
     */
    @GetMapping("/{workspaceId}")
    @Operation(summary = "Fetch one workspace")
    WorkspaceResponse get(@PathVariable UUID workspaceId) {
        guard.requirePermission(workspaceId, Permissions.WORKSPACE_READ);
        return provisioning.get(workspaceId);
    }

    @PatchMapping("/{workspaceId}")
    @Operation(
            summary = "Edit workspace settings",
            description = "Omitted fields are left alone. The slug cannot be changed")
    WorkspaceResponse update(@PathVariable UUID workspaceId, @Valid @RequestBody UpdateWorkspaceRequest request) {
        guard.requirePermissionToChange(workspaceId, Permissions.WORKSPACE_UPDATE);
        return lifecycle.update(workspaceId, request);
    }

    /**
     * Freezes the workspace. Its contents stay readable and nothing inside it may be changed.
     *
     * <p>Its own permission rather than {@code workspace:update}, because this is a lifecycle
     * decision about the whole workspace rather than an edit to one of its fields.
     */
    @PostMapping("/{workspaceId}/archive")
    @Operation(summary = "Archive a workspace", description = "Reversible; freezes every change inside it")
    WorkspaceResponse archive(@PathVariable UUID workspaceId) {
        // Not requirePermissionToChange: archiving an archived workspace is the
        // conflict, and the service says so.
        guard.requirePermission(workspaceId, Permissions.WORKSPACE_ARCHIVE);
        return lifecycle.archive(workspaceId, CurrentUser.requireId());
    }

    @PostMapping("/{workspaceId}/unarchive")
    @Operation(summary = "Restore an archived workspace")
    WorkspaceResponse unarchive(@PathVariable UUID workspaceId) {
        guard.requirePermission(workspaceId, Permissions.WORKSPACE_ARCHIVE);
        return lifecycle.unarchive(workspaceId);
    }

    /**
     * Removes a workspace and releases its slug.
     *
     * <p>Platform administration, and deliberately not something a workspace administrator can do to
     * their own workspace. {@code workspace:delete} is granted to no workspace role.
     */
    @DeleteMapping("/{workspaceId}")
    @PreAuthorize("@perm.onPlatform('workspace:delete')")
    @Operation(summary = "Remove a workspace", description = "Soft delete; the slug becomes available again")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId) {
        lifecycle.delete(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
