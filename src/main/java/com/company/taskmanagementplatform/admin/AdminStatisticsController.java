package com.company.taskmanagementplatform.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.activity.dto.ActivityResponse;
import com.company.taskmanagementplatform.admin.dto.SystemStatisticsResponse;
import com.company.taskmanagementplatform.common.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Platform-wide figures and the platform audit trail.
 *
 * <p><strong>This is the one part of the platform that crosses workspaces, and the rule that makes
 * it safe is stated once here.</strong> Architecture.md says cross-workspace reads are prevented by
 * construction and that the workspace comes from the request path. Both hold everywhere except
 * under {@code /admin}, and the carve-out is narrow on purpose:
 *
 * <ul>
 *   <li>Every route here is gated by {@code @perm.onPlatform} and nothing else. That resolver reads
 *       only the caller's platform role and never consults workspace membership, so administering
 *       one workspace reaches none of this however wide those grants are.
 *   <li>No route here takes a workspace identifier as an authorization input. Where one appears it
 *       is a filter over an already-authorized platform read, never a scope that widens anything.
 *   <li>Nothing here uses {@code WorkspaceAccessGuard}, because its 404-for-invisible rule makes no
 *       sense for a caller who can see everything.
 * </ul>
 *
 * <p>The rule in one line: an admin endpoint is platform-scoped or it is not an admin endpoint.
 * Anything narrower belongs in the module that owns the workspace-scoped rule, which is why the role
 * editor and the account verbs live in {@code workspaces}, {@code users} and {@code auth} rather
 * than here.
 *
 * <p>These answer <strong>403</strong> rather than 404 for a caller who may not use them, unlike
 * every workspace-scoped endpoint. That rule exists because "this workspace exists" is itself
 * information; these routes name no resource, so there is nothing to conceal the existence of and a
 * 404 would only make the API harder to use for the people entitled to it.
 *
 * <p>Nothing here writes, so nothing answers 409 and nothing asks whether anything is archived.
 */
@RestController
@RequestMapping("${app.api.base-path}/admin")
@Tag(name = "Admin", description = "Platform-wide statistics and the platform audit trail")
class AdminStatisticsController {

    private final AdminService admin;

    AdminStatisticsController(AdminService admin) {
        this.admin = admin;
    }

    /**
     * Every figure the panel shows, across every workspace.
     *
     * <p>Not paged, because it is a fixed set of panels rather than a listing. It is bounded instead
     * by the window, which {@code app.admin.max-stats-window-days} caps.
     */
    @GetMapping("/statistics")
    @PreAuthorize("@perm.onPlatform('admin:read_system')")
    @Operation(
            summary = "Platform statistics",
            description = "Accounts, workspaces, work, storage and recent activity across the installation")
    SystemStatisticsResponse statistics(@RequestParam(required = false) Integer windowDays) {
        return admin.statistics(windowDays);
    }

    /**
     * The platform audit trail: what happened outside any workspace.
     *
     * <p><strong>Two permissions, not either.</strong> {@code admin:read_system} says the caller may
     * look across the installation; {@code activity:read} says they may read an audit trail at all.
     * Somebody holding only the first has no business obtaining audit rows, and somebody holding
     * only the second reads their own workspace's history and not this.
     *
     * <p>Disjoint from the workspace history by construction: a row appears in exactly one of the
     * two, decided by whether its workspace is null. Neither can become a way into the other.
     */
    @GetMapping("/activity")
    @PreAuthorize("@perm.onPlatform('admin:read_system') and @perm.onPlatform('activity:read')")
    @Operation(
            summary = "The platform audit trail",
            description = "Account administration and platform-role grants. Newest first")
    PageResponse<ActivityResponse> activity(@PageableDefault(size = 50) Pageable pageable) {
        return admin.platformActivity(
                AdminSorts.capped(pageable, admin.properties().maxPageSize()));
    }
}
