package com.company.taskmanagementplatform.subtasks;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.projects.ProjectEvents;
import com.company.taskmanagementplatform.tasks.TaskEvents;
import com.company.taskmanagementplatform.users.UserDeletedEvent;
import com.company.taskmanagementplatform.workspaces.WorkspaceMemberRemovedEvent;

/**
 * Stands the subtasks module down when something above it goes away.
 *
 * <p>A subtask's assignee is a foreign key into {@code project_members}, exactly as a task's is, so
 * this carries the same obligation and the same ordering. It runs at {@link
 * Ordered#HIGHEST_PRECEDENCE}, ahead of {@code ProjectCleanupListener}, which deletes the membership
 * rows the key points at.
 *
 * <p>The cascades are the other half. A soft-deleted task must take its checklist with it, or the
 * items would go on counting toward the project's progress and would be reachable through a parent
 * that no read can see.
 */
@Component
class SubtaskCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(SubtaskCleanupListener.class);

    private final SubtaskRepository subtasks;
    private final Clock clock;

    SubtaskCleanupListener(SubtaskRepository subtasks, Clock clock) {
        this.subtasks = subtasks;
        this.clock = clock;
    }

    /**
     * Somebody left a workspace: unassign their checklist items everywhere in it.
     *
     * <p>Deliberately not filtered on {@code deleted_at}. The foreign key holds for a soft-deleted row
     * just as it does for a live one, so clearing only the live ones would leave the removal refused
     * for a reason nothing on screen could explain.
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    void onWorkspaceMemberRemoved(WorkspaceMemberRemovedEvent event) {
        clearAssignees(
                subtasks.findAllByWorkspaceIdAndAssigneeUserId(event.workspaceId(), event.userId()),
                "a removed workspace member",
                event.userId());
    }

    /** Somebody left one project: unassign only the items on that project. */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    void onProjectMemberRemoved(ProjectEvents.ProjectMemberRemoved event) {
        clearAssignees(
                subtasks.findAllByProjectIdAndAssigneeUserIdAndDeletedAtIsNull(event.projectId(), event.userId()),
                "a removed project member",
                event.userId());
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    void onUserDeleted(UserDeletedEvent event) {
        clearAssignees(subtasks.findAllByAssigneeUserId(event.userId()), "a deleted account", event.userId());
    }

    /** A task was removed, so its checklist goes with it. */
    @EventListener
    @Transactional
    void onTaskDeleted(TaskEvents.TaskDeleted event) {
        softDelete(subtasks.findAllByTaskIdAndDeletedAtIsNull(event.taskId()), "task", event.taskId());
    }

    /**
     * A project was removed, so every checklist inside it goes too.
     *
     * <p>Listened for directly rather than through the per-task deletions the tasks module performs,
     * because one project can hold thousands of tasks and a burst of one event each would be a
     * needless storm for anything listening in a later phase.
     */
    @EventListener
    @Transactional
    void onProjectDeleted(ProjectEvents.ProjectDeleted event) {
        softDelete(subtasks.findAllByProjectIdAndDeletedAtIsNull(event.projectId()), "project", event.projectId());
    }

    private void clearAssignees(List<Subtask> found, String because, UUID userId) {
        if (found.isEmpty()) {
            return;
        }
        found.forEach(Subtask::clearAssignee);
        subtasks.flush();
        log.info("Unassigned {} subtask(s) from {}: userId={}", found.size(), because, userId);
    }

    private void softDelete(List<Subtask> found, String parentKind, UUID parentId) {
        if (found.isEmpty()) {
            return;
        }
        found.forEach(subtask -> subtask.softDelete(clock.instant()));
        subtasks.flush();
        log.info("Removed {} subtask(s) with a deleted {}: id={}", found.size(), parentKind, parentId);
    }
}
