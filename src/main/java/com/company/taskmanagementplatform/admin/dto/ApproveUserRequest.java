package com.company.taskmanagementplatform.admin.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What an administrator decides when letting somebody into a workspace.
 *
 * <p>The workspace is not here. It is the one in the path, and that is deliberate rather than
 * tidiness: the caller's right to approve is checked against that workspace, so taking it from a
 * body field would mean the thing being authorised and the thing being written were two separate
 * values that had to be kept in step. In the path they are the same value.
 *
 * @param roleSlug the role to hold there: {@code ADMIN}, {@code TEAM_LEAD} or {@code EMPLOYEE}. Must
 *     be a role of that workspace, which the membership service checks again
 * @param projectId an optional project of the same workspace to join at the same time. Naming one
 *     needs {@code project:manage_members} as well, so an administrator cannot reach a project
 *     through an approval that they could not reach directly
 */
@Schema(name = "ApproveUserRequest")
public record ApproveUserRequest(
        @NotBlank @Schema(example = "EMPLOYEE", description = "A role of this workspace") String roleSlug,
        @Schema(description = "Optional; a project of this workspace to join immediately") UUID projectId) {}
