package com.company.taskmanagementplatform.workspaces;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.workspaces.dto.UpdateWorkspaceRequest;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * Editing a workspace's settings, and moving it through its lifecycle.
 *
 * <p>Archiving and deleting are separate operations because they answer different questions.
 * Archiving says this workspace is finished with but worth keeping: it is reversible, and while it
 * lasts nothing inside the workspace may be changed. Deleting says it should not have existed: it
 * hides the workspace and releases its slug, and it is the platform administrator's call alone.
 *
 * <p>The freeze is enforced by {@link WorkspaceAccessGuard#requireActiveWorkspace}, which every
 * mutating endpoint inside a workspace calls. Putting it there rather than in each service is what
 * makes it apply to the modules built after this one without their having to remember it.
 */
@Service
public class WorkspaceLifecycleService {

    private final WorkspaceRepository workspaces;
    private final RoleRepository roles;
    private final Clock clock;

    WorkspaceLifecycleService(WorkspaceRepository workspaces, RoleRepository roles, Clock clock) {
        this.workspaces = workspaces;
        this.roles = roles;
        this.clock = clock;
    }

    /**
     * Applies the fields the request actually carried.
     *
     * <p>Null means the client did not send the field and it is left alone. The one exception is the
     * description, where a blank value clears it, because there has to be some way to remove a value
     * that was set by mistake.
     */
    @Transactional
    public WorkspaceResponse update(UUID workspaceId, UpdateWorkspaceRequest request) {
        Workspace workspace = require(workspaceId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("A workspace needs a name.");
            }
            workspace.rename(name);
        }

        if (request.description() != null) {
            String description = request.description().trim();
            workspace.describe(description.isEmpty() ? null : description);
        }

        if (request.timezone() != null) {
            workspace.moveToTimezone(requireKnownZone(request.timezone().trim()));
        }

        if (request.defaultRoleSlug() != null) {
            Role role = roles.findByWorkspaceIdAndSlug(workspaceId, request.defaultRoleSlug())
                    .orElseThrow(() -> new BadRequestException("That role does not exist in this workspace."));
            workspace.useDefaultRole(role.getId());
        }

        return WorkspaceMapper.toResponse(workspace, defaultRoleSlug(workspace));
    }

    @Transactional
    public WorkspaceResponse archive(UUID workspaceId, UUID byUserId) {
        Workspace workspace = require(workspaceId);
        if (workspace.isArchived()) {
            throw new ConflictException("This workspace is already archived.");
        }
        workspace.archive(byUserId, clock.instant());
        return WorkspaceMapper.toResponse(workspace, defaultRoleSlug(workspace));
    }

    @Transactional
    public WorkspaceResponse unarchive(UUID workspaceId) {
        Workspace workspace = require(workspaceId);
        if (!workspace.isArchived()) {
            throw new ConflictException("This workspace is not archived.");
        }
        workspace.unarchive();
        return WorkspaceMapper.toResponse(workspace, defaultRoleSlug(workspace));
    }

    /**
     * Hides the workspace and releases its slug.
     *
     * <p>Nothing below it is touched. Its roles, memberships and teams stay exactly as they were, so
     * the row can be restored by an operator if the deletion was a mistake. Every read path filters
     * on {@code deleted_at}, so none of it is reachable in the meantime.
     */
    @Transactional
    public void delete(UUID workspaceId) {
        require(workspaceId).softDelete(clock.instant());
    }

    /**
     * Rejects a zone the JVM does not know.
     *
     * <p>Checked here rather than by a check constraint on purpose: the zone database is updated
     * several times a year, and freezing a copy of it into the schema would mean a migration every
     * time a country moved its clocks.
     */
    private static String requireKnownZone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("That is not a recognised time zone.");
        }
    }

    private Workspace require(UUID workspaceId) {
        return workspaces
                .findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace", workspaceId));
    }

    private String defaultRoleSlug(Workspace workspace) {
        return workspace.getDefaultRoleId() == null
                ? null
                : roles.findById(workspace.getDefaultRoleId())
                        .map(Role::getSlug)
                        .orElse(null);
    }
}
