package com.company.taskmanagementplatform.teams;

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

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.teams.dto.CreateTeamRequest;
import com.company.taskmanagementplatform.teams.dto.TeamResponse;
import com.company.taskmanagementplatform.teams.dto.UpdateTeamRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The teams of one workspace.
 *
 * <p>The workspace comes from the path and never from a header, so it can never be inherited from
 * ambient state. Every method starts at {@link TeamAccessGuard}, which answers 404 for a workspace or
 * a team the caller cannot see, 403 for one they can see but may not act on, and 409 for a workspace
 * that has been archived.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/teams")
@Tag(name = "Teams", description = "Teams within a workspace, their lifecycle and their leads")
class TeamController {

    private final TeamService teams;
    private final TeamAccessGuard guard;

    TeamController(TeamService teams, TeamAccessGuard guard) {
        this.teams = teams;
        this.guard = guard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a team", description = "A named lead is added to the team as well")
    TeamResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody CreateTeamRequest request) {
        guard.requireCreateAccess(workspaceId);
        return teams.create(workspaceId, request, CurrentUser.requireId());
    }

    @GetMapping
    @Operation(summary = "List the teams of a workspace", description = "Optionally filtered by status")
    PageResponse<TeamResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        guard.requireReadAccess(workspaceId);
        return PageResponse.of(teams.list(workspaceId, parseStatus(status), pageable), team -> team);
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "Fetch one team")
    TeamResponse get(@PathVariable UUID workspaceId, @PathVariable UUID teamId) {
        guard.requireReadableTeam(workspaceId, teamId);
        return teams.describe(workspaceId, teamId);
    }

    @PatchMapping("/{teamId}")
    @Operation(summary = "Edit a team", description = "Omitted fields are left alone")
    TeamResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID teamId,
            @Valid @RequestBody UpdateTeamRequest request) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_UPDATE);
        return teams.update(workspaceId, teamId, request);
    }

    @PostMapping("/{teamId}/archive")
    @Operation(summary = "Archive a team", description = "Reversible; the roster and the lead are kept")
    TeamResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID teamId) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_UPDATE);
        return teams.archive(workspaceId, teamId);
    }

    @PostMapping("/{teamId}/unarchive")
    @Operation(summary = "Restore an archived team")
    TeamResponse unarchive(@PathVariable UUID workspaceId, @PathVariable UUID teamId) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_UPDATE);
        return teams.unarchive(workspaceId, teamId);
    }

    /**
     * Removes a team and frees its name.
     *
     * <p>{@code team:delete} is held by the workspace administrator and not by a team lead, so this
     * is the one team operation a lead cannot perform on their own team.
     */
    @DeleteMapping("/{teamId}")
    @Operation(summary = "Remove a team", description = "Soft delete; the name becomes available again")
    ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID teamId) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_DELETE);
        teams.delete(workspaceId, teamId);
        return ResponseEntity.noContent().build();
    }

    /** Rejected here rather than coerced, so a typo does not silently return every team. */
    private static TeamStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TeamStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("That is not a team status.");
        }
    }
}
