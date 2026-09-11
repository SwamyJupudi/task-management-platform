package com.company.taskmanagementplatform.tasks;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.CurrentUser;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectContext;
import com.company.taskmanagementplatform.projects.ProjectScope;
import com.company.taskmanagementplatform.workspaces.WorkspaceAccessGuard;

/**
 * The gate on every task endpoint, and the third link in a chain that starts at the workspace.
 *
 * <p>{@code WorkspaceAccessGuard} then {@code ProjectAccessFacade} then this. The questions are
 * asked in this order because each may only be put to somebody who has already passed the one before
 * it:
 *
 * <ol>
 *   <li>Can the caller see this workspace at all? No, and the answer is 404, from the workspace
 *       guard.
 *   <li>Do they hold the permission this operation needs? No, and the answer is 403.
 *   <li>Is the workspace still open for changes? Archived, and the answer is 409. Asked only for
 *       operations that change something.
 *   <li>Does this task exist in <em>this</em> workspace? No, and the answer is 404.
 *   <li>Is the task's project one the caller may see? No, and the answer is 404, deliberately
 *       indistinguishable from a task that never existed.
 *   <li>Is the project still open for changes? Archived, and the answer is 409.
 *   <li>May they act on this particular task? No, and the answer is 403.
 * </ol>
 *
 * <p>Steps five and seven are the two halves of the scope layer and their order is load-bearing.
 * Read scope is checked first, so something the caller may not even see never answers 403; write
 * scope is checked after, so something they can see in a list answers 403 rather than pretending to
 * be missing, which would be misleading rather than discreet.
 *
 * <p><strong>Task visibility is project visibility.</strong> There is no {@code task:read_any}: a
 * task is visible exactly when its project is, so {@code project:read_any} widens both at once. The
 * assignee is a foreign key into {@code project_members}, so a task can never be assigned to
 * somebody outside the project that holds it, and "tasks assigned to me" is a subset of "tasks I can
 * see" by construction rather than by a check.
 *
 * <p>The write scope, for a caller without {@code task:manage_any}: the assignee, the reporter, the
 * owner of the project, or the lead of the project's team. An employee therefore edits their own
 * work and the tickets they raised, and a lead reaches everything in the projects they run.
 *
 * <p>These methods authorize and return identifiers, never entities. The guard runs in its own
 * read-only transaction, so an entity returned from here would reach the service already detached
 * and every change made to it would be discarded without an error.
 *
 * <p>Public, unlike the guards in {@code teams} and {@code projects}, because {@code subtasks},
 * {@code comments} and {@code attachments} all authorize through the parent task. A second copy of
 * these seven checks would be worse.
 */
@Component
public class TaskAccessGuard {

    private final WorkspaceAccessGuard workspaceGuard;
    private final ProjectAccessFacade projects;
    private final TaskRepository tasks;

    TaskAccessGuard(WorkspaceAccessGuard workspaceGuard, ProjectAccessFacade projects, TaskRepository tasks) {
        this.workspaceGuard = workspaceGuard;
        this.projects = projects;
        this.tasks = tasks;
    }

    /** Listing tasks, which names none and is narrowed by the project scope instead. */
    @Transactional(readOnly = true)
    public ProjectScope requireListAccess(UUID workspaceId) {
        Set<String> granted = requirePermission(workspaceId, Permissions.TASK_READ);
        return scopeFrom(workspaceId, granted);
    }

    /**
     * Listing the tasks of one named project, which must itself be readable.
     *
     * <p>A project the caller cannot see answers 404 rather than an empty page, because an empty page
     * would say the project exists and happens to be empty.
     */
    @Transactional(readOnly = true)
    public ProjectScope requireProjectListAccess(UUID workspaceId, UUID projectId) {
        Set<String> granted = requirePermission(workspaceId, Permissions.TASK_READ);
        requireReadableProject(workspaceId, projectId, granted);
        return scopeFrom(workspaceId, granted);
    }

    /**
     * Creating a task in a project.
     *
     * @return the project, so the service does not have to look it up again
     */
    @Transactional(readOnly = true)
    public ProjectContext requireCreateAccess(UUID workspaceId, UUID projectId) {
        Set<String> granted = requirePermission(workspaceId, Permissions.TASK_CREATE);
        workspaceGuard.requireActiveWorkspace(workspaceId);

        ProjectContext project = requireReadableProject(workspaceId, projectId, granted);
        requireProjectNotArchived(project);
        return project;
    }

    /**
     * One task the caller may read.
     *
     * @throws ResourceNotFoundException if it does not exist here, or is not one of theirs to see
     */
    @Transactional(readOnly = true)
    public TaskRef requireReadableTask(UUID workspaceId, UUID taskId) {
        Set<String> granted = requirePermission(workspaceId, Permissions.TASK_READ);

        Task task = requireTaskInWorkspace(workspaceId, taskId);
        requireReadableProject(workspaceId, task.getProjectId(), granted, taskId);

        return new TaskRef(task.getId(), task.getProjectId(), workspaceId);
    }

    /**
     * One task the caller may change, resolving the permission and both scopes together.
     *
     * <p>The permission set is read once and every question answered from it, rather than resolving
     * it three times for what is one decision.
     */
    @Transactional(readOnly = true)
    public TaskRef requireChangeableTask(UUID workspaceId, UUID taskId, String permissionCode) {
        Set<String> granted = requirePermission(workspaceId, permissionCode);
        workspaceGuard.requireActiveWorkspace(workspaceId);

        Task task = requireTaskInWorkspace(workspaceId, taskId);

        // Read scope first: something they may not even see must not answer 403.
        ProjectContext project = requireReadableProject(workspaceId, task.getProjectId(), granted, taskId);
        requireProjectNotArchived(project);

        if (!granted.contains(Permissions.TASK_MANAGE_ANY) && !canChange(task, project)) {
            // Visible to them, so 403 rather than 404. Pretending it were missing
            // would be misleading: they can see it in the list they just fetched.
            throw new AccessDeniedException(
                    "Only the assignee, the reporter, the project owner or its team lead may change this task");
        }

        return new TaskRef(task.getId(), task.getProjectId(), workspaceId);
    }

    /**
     * A task the caller may add a comment or a file to.
     *
     * <p>Contributing is not the same as changing. Anybody who can see a task may comment on it and
     * attach to it, so the write scope of {@link #requireChangeableTask} is deliberately absent here:
     * an employee on a project who is neither the assignee nor the reporter of a ticket can still
     * take part in the discussion on it, which is the whole point of a discussion.
     *
     * <p>What is still checked is everything else: the named permission, {@code task:read} because a
     * comment is visible exactly when its task is, the workspace freeze, the project's readability,
     * and the project's own freeze.
     */
    @Transactional(readOnly = true)
    public TaskRef requireContributableTask(UUID workspaceId, UUID taskId, String permissionCode) {
        return requireContribution(workspaceId, taskId, permissionCode, null).task();
    }

    /**
     * The same, and whether the caller reaches contributions that are not their own.
     *
     * @param moderationCode the {@code manage_any} grant for the thing being reached, or null when
     *     the answer does not matter
     */
    @Transactional(readOnly = true)
    public TaskContribution requireContribution(
            UUID workspaceId, UUID taskId, String permissionCode, String moderationCode) {

        Set<String> granted = requirePermission(workspaceId, permissionCode);
        if (!granted.contains(Permissions.TASK_READ)) {
            throw new AccessDeniedException("Missing permission " + Permissions.TASK_READ);
        }
        workspaceGuard.requireActiveWorkspace(workspaceId);

        Task task = requireTaskInWorkspace(workspaceId, taskId);

        // Read scope first, for the reason requireChangeableTask gives.
        ProjectContext project = requireReadableProject(workspaceId, task.getProjectId(), granted, taskId);
        requireProjectNotArchived(project);

        boolean moderator =
                (moderationCode != null && granted.contains(moderationCode)) || project.ownedOrLed();

        return new TaskContribution(new TaskRef(task.getId(), task.getProjectId(), workspaceId), moderator);
    }

    private Set<String> requirePermission(UUID workspaceId, String permissionCode) {
        Set<String> granted = workspaceGuard.visiblePermissions(workspaceId);
        if (!granted.contains(permissionCode)) {
            throw new AccessDeniedException("Missing permission " + permissionCode);
        }
        return granted;
    }

    /** The reach of a caller who lacks {@code project:read_any}, for the listing query. */
    private ProjectScope scopeFrom(UUID workspaceId, Set<String> granted) {
        return projects.readableScope(
                workspaceId, CurrentUser.requireId(), granted.contains(Permissions.PROJECT_READ_ANY));
    }

    private ProjectContext requireReadableProject(UUID workspaceId, UUID projectId, Set<String> granted) {
        return requireReadableProject(workspaceId, projectId, granted, projectId);
    }

    /**
     * The project a task lives in, if the caller may see it.
     *
     * <p>{@code missingId} is what the 404 names. When the caller came in through a task it names the
     * task, so an unreachable project never reveals that it exists by being the thing reported
     * missing.
     */
    private ProjectContext requireReadableProject(
            UUID workspaceId, UUID projectId, Set<String> granted, UUID missingId) {

        ProjectContext project = projects
                .contextOf(workspaceId, projectId, CurrentUser.requireId(), granted.contains(Permissions.PROJECT_READ_ANY))
                .orElseThrow(() -> ResourceNotFoundException.of("Task", missingId));

        if (!project.readable()) {
            // Deliberately indistinguishable from something that does not exist.
            throw ResourceNotFoundException.of("Task", missingId);
        }
        return project;
    }

    private static void requireProjectNotArchived(ProjectContext project) {
        if (project.archived()) {
            throw new ConflictException("That project is archived. Move it out of archive before changing its work.");
        }
    }

    /** Assignee, reporter, project owner, or lead of the project's team. */
    private static boolean canChange(Task task, ProjectContext project) {
        UUID userId = CurrentUser.requireId();
        return task.isAssignedTo(userId) || task.isReportedBy(userId) || project.ownedOrLed();
    }

    /**
     * A task of this workspace, or nothing.
     *
     * <p>The workspace is part of the lookup rather than checked afterwards, so a task identifier
     * from another workspace is indistinguishable from one that was never real.
     */
    private Task requireTaskInWorkspace(UUID workspaceId, UUID taskId) {
        return tasks.findByIdAndWorkspaceIdAndDeletedAtIsNull(taskId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", taskId));
    }
}
