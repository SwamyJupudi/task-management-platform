package com.company.taskmanagementplatform.teams;

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
import com.company.taskmanagementplatform.teams.dto.AddTeamMemberRequest;
import com.company.taskmanagementplatform.teams.dto.AssignTeamLeadRequest;
import com.company.taskmanagementplatform.teams.dto.TeamMemberResponse;
import com.company.taskmanagementplatform.teams.dto.TeamResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The roster of one team, and who leads it.
 *
 * <p>Reading needs {@code team:read}, which every role holds. Changing needs {@code
 * team:manage_members}, and for anybody without {@code team:manage_any} the guard narrows that to
 * the teams they lead.
 *
 * <p>The lead is a subresource with its own two verbs rather than a field on the team, because
 * assigning one also puts that person in the team. That is worth being explicit about instead of
 * happening as a side effect of a general edit.
 */
@RestController
@RequestMapping("${app.api.base-path}/workspaces/{workspaceId}/teams/{teamId}")
@Tag(name = "Team members", description = "Team membership and leadership")
class TeamMemberController {

    private final TeamMembershipService memberships;
    private final TeamAccessGuard guard;

    TeamMemberController(TeamMembershipService memberships, TeamAccessGuard guard) {
        this.memberships = memberships;
        this.guard = guard;
    }

    @GetMapping("/members")
    @Operation(summary = "List the members of a team")
    PageResponse<TeamMemberResponse> listMembers(
            @PathVariable UUID workspaceId,
            @PathVariable UUID teamId,
            @PageableDefault(size = 20) Pageable pageable) {
        guard.requireReadableTeam(workspaceId, teamId);
        return PageResponse.of(memberships.listMembers(workspaceId, teamId, pageable), member -> member);
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a workspace member to a team")
    TeamMemberResponse addMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID teamId,
            @Valid @RequestBody AddTeamMemberRequest request) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_MANAGE_MEMBERS);
        return memberships.addMember(workspaceId, teamId, request.userId(), CurrentUser.requireId());
    }

    @DeleteMapping("/members/{userId}")
    @Operation(summary = "Remove somebody from a team", description = "Refuses if they lead it")
    ResponseEntity<Void> removeMember(
            @PathVariable UUID workspaceId, @PathVariable UUID teamId, @PathVariable UUID userId) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_MANAGE_MEMBERS);
        memberships.removeMember(workspaceId, teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/lead")
    @Operation(summary = "Assign the team lead", description = "Adds them to the team if they are not in it")
    TeamResponse assignLead(
            @PathVariable UUID workspaceId,
            @PathVariable UUID teamId,
            @Valid @RequestBody AssignTeamLeadRequest request) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_MANAGE_MEMBERS);
        return memberships.assignLead(workspaceId, teamId, request.userId(), CurrentUser.requireId());
    }

    @DeleteMapping("/lead")
    @Operation(summary = "Leave the team without a lead", description = "They stay a member of it")
    TeamResponse clearLead(@PathVariable UUID workspaceId, @PathVariable UUID teamId) {
        guard.requireChangeableTeam(workspaceId, teamId, Permissions.TEAM_MANAGE_MEMBERS);
        return memberships.clearLead(workspaceId, teamId);
    }
}
