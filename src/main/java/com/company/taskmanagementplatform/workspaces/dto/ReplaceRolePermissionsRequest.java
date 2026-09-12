package com.company.taskmanagementplatform.workspaces.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The complete set of permission codes a role should grant afterwards.
 *
 * <p>Replacement, not a delta, and the whole list every time. A body of additions and removals would
 * need the client to know the current state to compute it, and two administrators editing the same
 * role at once would silently merge into a set neither of them chose. The platform already made this
 * choice for labels.
 *
 * <p><strong>An empty list is valid</strong> and means the role grants nothing. That is a
 * legitimate thing to want, and it is a different statement from omitting the field, which is
 * refused.
 */
@Schema(name = "ReplaceRolePermissionsRequest")
public record ReplaceRolePermissionsRequest(
        @NotNull
                @Size(max = 200, message = "A role cannot be given more than 200 permissions.")
                @Schema(
                        description = "Every code the role should grant. Codes not listed are removed",
                        example = "[\"task:read\", \"task:create\"]")
                List<@NotBlank @Size(max = 100) String> permissions) {}
