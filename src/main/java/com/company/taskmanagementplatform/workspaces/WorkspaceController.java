package com.company.taskmanagementplatform.workspaces;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Creating and listing workspaces, both of which are platform administration.
 *
 * <p>Ordinary members do not list workspaces here. They receive the ones they belong to from the
 * endpoint that describes their session, which is a different question with a different answer:
 * "which workspaces exist" against "which are mine".
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces")
@Tag(name = "Workspaces", description = "Workspace creation and platform-wide listing")
class WorkspaceController {

    private final WorkspaceProvisioningService provisioning;
    private final WorkspaceAccessGuard guard;

    WorkspaceController(WorkspaceProvisioningService provisioning, WorkspaceAccessGuard guard) {
        this.provisioning = provisioning;
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
}
