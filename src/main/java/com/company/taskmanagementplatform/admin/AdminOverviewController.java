package com.company.taskmanagementplatform.admin;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.admin.dto.PlatformAccountResponse;
import com.company.taskmanagementplatform.admin.dto.PlatformProjectResponse;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.ProjectStatus;
import com.company.taskmanagementplatform.users.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The two cross-workspace listings: every project, and every account.
 *
 * <p>Split from {@link AdminStatisticsController} by subject rather than by audience. These page,
 * filter and sort; that one answers a fixed set of panels and a history. Folding them together would
 * produce one controller with a mode switch, and a mode switch is where authorization rules go to
 * get confused.
 *
 * <p>The platform-scope rule stated on that class applies here unchanged and in full: {@code
 * @perm.onPlatform} and nothing else, no workspace guard, and a {@code workspaceId} that is a filter
 * over an already-authorized read rather than a scope.
 *
 * <p>Both page, and both cap their page size. An installation's whole project list is precisely the
 * unbounded read the requirements warn about, and unlike every other listing in the platform there
 * is no tenant predicate narrowing it first.
 */
@RestController
@RequestMapping("${app.api.base-path}/admin")
@Tag(name = "Admin overview", description = "Projects and accounts across every workspace")
class AdminOverviewController {

    /** Most recently touched first, which is what somebody scanning an installation wants. */
    private static final Sort RECENTLY_UPDATED_FIRST = Sort.by(Sort.Direction.DESC, "updatedAt");

    private static final Sort NEWEST_ACCOUNTS_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AdminService admin;

    AdminOverviewController(AdminService admin) {
        this.admin = admin;
    }

    /**
     * Every project in the installation, filtered and paged.
     *
     * <p>Needs {@code admin:read_system} and deliberately <strong>not</strong> {@code
     * project:read_any}. That code is workspace-scoped and three seeded roles can hold it; checking
     * it here through {@code onPlatform} would ask a question about a platform role that nothing
     * grants it, and checking it through {@code inWorkspace} would need a workspace this endpoint
     * does not have. The platform grant is the whole gate.
     */
    @GetMapping("/projects")
    @PreAuthorize("@perm.onPlatform('admin:read_system')")
    @Operation(
            summary = "Every project across every workspace",
            description = "Each with its workspace name and derived progress. Most recently updated first")
    PageResponse<PlatformProjectResponse> projects(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) UUID teamId,
            @PageableDefault(size = 20) Pageable pageable) {

        return admin.projectOverview(
                workspaceId,
                status,
                ownerUserId,
                teamId,
                AdminSorts.paged(
                        pageable,
                        AdminSorts.PROJECTS,
                        RECENTLY_UPDATED_FIRST,
                        admin.properties().maxPageSize()));
    }

    /**
     * The administrative account directory.
     *
     * <p>{@code user:read}, the same code the ordinary directory at {@code GET /users} uses, because
     * it is the same disclosure with two extra columns. This does not duplicate that endpoint: it
     * adds how many workspaces each person belongs to and whether they hold the platform role, both
     * resolved for the whole page in one query.
     *
     * <p>The {@code locked} filter is the one worth naming. A locked account is not a deactivated
     * one, and "who cannot sign in and does not know why" is the question an administrator most
     * often opens this page to answer.
     */
    @GetMapping("/accounts")
    @PreAuthorize("@perm.onPlatform('user:read')")
    @Operation(
            summary = "Every account, with its reach across the installation",
            description = "Adds workspace count and platform role to the ordinary account directory")
    PageResponse<PlatformAccountResponse> accounts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean locked,
            @PageableDefault(size = 20) Pageable pageable) {

        return admin.accounts(
                search,
                status,
                locked,
                AdminSorts.paged(
                        pageable,
                        AdminSorts.ACCOUNTS,
                        NEWEST_ACCOUNTS_FIRST,
                        admin.properties().maxPageSize()));
    }
}
