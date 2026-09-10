package com.company.taskmanagementplatform.workspaces;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.workspaces.dto.PermissionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The permission catalog, which is global rather than per workspace.
 *
 * <p>Read by the admin panel to render the role editor, and by nobody else. The rows are written by
 * migrations, so there is no write endpoint here and there will not be one.
 */
@RestController
@RequestMapping("${app.api.base-path}/permissions")
@Tag(name = "Permissions", description = "The global catalog of capabilities")
class PermissionController {

    private final RoleQueryService roles;

    PermissionController(RoleQueryService roles) {
        this.roles = roles;
    }

    @GetMapping
    @PreAuthorize("@perm.onPlatform('permission:read')")
    @Operation(summary = "List every permission the application implements")
    List<PermissionResponse> list() {
        return roles.listCatalog();
    }
}
