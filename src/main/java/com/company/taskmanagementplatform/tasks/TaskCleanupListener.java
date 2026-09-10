package com.company.taskmanagementplatform.tasks;

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
import com.company.taskmanagementplatform.users.UserDeletedEvent;
import com.company.taskmanagementplatform.workspaces.WorkspaceMemberRemovedEvent;

/**
 * Stands the tasks module down when something underneath it goes away.
 *
 * <p>Two columns here depend on a membership, and both are foreign keys. The assignee keys into
 * {@code project_members} and the reporter into {@code workspace_members}, so until this has run
 * PostgreSQL refuses to delete either row. This is the step that makes those removals possible
 * rather than tidying that could be deferred.
 *
 * <p><strong>Ordered first, ahead of {@code ProjectCleanupListener}.</strong> That listener deletes
 * the {@code project_members} rows, and the foreign key from {@code tasks} refuses while somebody
 * still holds a task. The flush is the other half: Hibernate may order the statements in a flush by
 * entity type rather than by call order, so the work is forced out before the publisher continues.
 *
 * <p>The {@link Order} annotations are on the <em>methods</em> and not on the class, and that is not
 * a style choice. {@code ApplicationListenerMethodAdapter} resolves a listener's order from the
 * annotated method alone; a class-level {@code @Order} is silently ignored for an {@code
 * @EventListener}, leaving every listener at {@code LOWEST_PRECEDENCE} and the sequence up to bean
 * registration order. This was found by {@code TaskCascadeIT} failing exactly the way an unordered
 * listener would.
 *
 * <p>Assignment is cleared rather than handed on, because choosing who picks up somebody's work is
 * not a decision this code is in a position to make.
 */
@Component
class TaskCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(TaskCleanupListener.class);

    private final TaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final Clock clock;

    TaskCleanupListener(TaskRepository tasks, TaskDependencyRepository dependencies, Clock clock) {
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.clock = clock;
    }

    /**
     * Somebody left a workspace: unassign their tasks and clear them as reporter.
     *
     * <p>The reporter sweep is deliberately not filtered on {@code deleted_at}. The foreign key holds
     * for a soft-deleted row just as it does for a live one, so clearing only the live ones would
     * leave the removal refused for a reason nothing on screen could explain.
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    void onWorkspaceMemberRemoved(WorkspaceMemberRemovedEvent event) {
        List<Task> assigned =
                tasks.findAllByWorkspaceIdAndAssigneeUserIdAndDeletedAtIsNull(event.workspaceId(), event.userId());
        assigned.forEach(Task::clearAssignee);

        List<Task> reported = tasks.findAllByWorkspaceIdAndReporterUserId(event.workspaceId(), event.userId());
        reported.forEach(Task::clearReporter);

        tasks.flush();

        if (!assigned.isEmpty() || !reported.isEmpty()) {
            log.info(
                    "Cleared task state for a removed workspace member: workspaceId={} userId={} unassigned={} unreported={}",
                    event.workspaceId(),
                    event.userId(),
                    assigned.size(),
                    reported.size());
        }
    }

    /** Somebody left one project: unassign only the tasks on that project. */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    void onProjectMemberRemoved(ProjectEvents.ProjectMemberRemoved event) {
        List<Task> assigned =
                tasks.findAllByProjectIdAndAssigneeUserIdAndDeletedAtIsNull(event.projectId(), event.userId());
        assigned.forEach(Task::clearAssignee);
        tasks.flush();

        if (!assigned.isEmpty()) {
            log.info(
                    "Unassigned {} task(s) from a removed project member: projectId={} userId={}",
                    assigned.size(),
                    event.projectId(),
                    event.userId());
        }
    }

    /**
     * The same cleanup, across every workspace at once.
     *
     * <p>A deleted account may have held tasks in several workspaces, and the membership listener in
     * {@code workspaces} removes all of its rows in one statement, so nothing narrower would do.
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    void onUserDeleted(UserDeletedEvent event) {
        UUID userId = event.userId();

        List<Task> assigned = tasks.findAllByAssigneeUserIdAndDeletedAtIsNull(userId);
        assigned.forEach(Task::clearAssignee);

        List<Task> reported = tasks.findAllByReporterUserId(userId);
        reported.forEach(Task::clearReporter);

        tasks.flush();

        if (!assigned.isEmpty() || !reported.isEmpty()) {
            log.info(
                    "Cleared task state for a deleted account: userId={} unassigned={} unreported={}",
                    userId,
                    assigned.size(),
                    reported.size());
        }
    }

    /**
     * A project was removed, so its work goes with it.
     *
     * <p>The alternative was to leave the tasks live and filter them out of every read, and it is
     * worse than it sounds: an administrator holds the workspace-wide read grant, so their listing
     * narrows on nothing, and a deleted project's tasks would keep appearing in their board and their
     * calendar with no project to click through to. A soft delete is reversible in principle, and the
     * projects module does not restore projects yet, so nothing is lost that could be got back.
     */
    @EventListener
    @Transactional
    void onProjectDeleted(ProjectEvents.ProjectDeleted event) {
        List<Task> live = tasks.findAllByProjectIdAndDeletedAtIsNull(event.projectId());
        if (live.isEmpty()) {
            return;
        }

        for (Task task : live) {
            dependencies.deleteAllByTaskIdOrDependsOnTaskId(task.getId(), task.getId());
            task.softDelete(clock.instant());
        }
        tasks.flush();

        log.info(
                "Removed {} task(s) with a deleted project: workspaceId={} projectId={}",
                live.size(),
                event.workspaceId(),
                event.projectId());
    }
}
