package com.company.taskmanagementplatform.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.projects.ProjectEvents;
import com.company.taskmanagementplatform.users.UserDeletedEvent;
import com.company.taskmanagementplatform.workspaces.WorkspaceMemberRemovedEvent;

/**
 * Takes somebody's notifications away when they lose the access those notifications imply.
 *
 * <p>Phase six's modules deliberately have no listener like this, because a comment must outlive its
 * author changing team and an audit row whose actor could vanish would not be an audit row. A
 * notification is the opposite kind of thing. It is not a record of anything: it is a message saying
 * "come and look at this", and a message pointing at a project somebody was removed from should not
 * sit in their feed offering them a link they can no longer follow.
 *
 * <p><strong>Hard deletion, not soft.</strong> The table has no {@code deleted_at} and
 * {@code database.md} has said so since the model was drawn. There is nothing to restore: if the
 * person comes back, what they need is the current state of the work, not a two-month-old message
 * about a status change that has since changed again.
 *
 * <p>Three events and deliberately not a fourth. A soft-deleted task or comment triggers nothing
 * here: the row stays, and the read path renders it without a link once the target is no longer
 * visible. The alternative, chasing every cascade, would mean this module listening to half the
 * platform to keep a feed tidy.
 *
 * <p>These run inside the caller's transaction rather than after it, unlike everything else in this
 * module. They are deletions that must not survive a rollback of the removal that caused them, and
 * they are bounded by one person's rows rather than by a queue.
 */
@Component
class NotificationCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupListener.class);

    private final NotificationRepository notifications;

    NotificationCleanupListener(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Somebody left the workspace: everything they were told about it goes with them. */
    @EventListener
    @Transactional
    void onWorkspaceMemberRemoved(WorkspaceMemberRemovedEvent event) {
        int removed = notifications.deleteForMemberOfWorkspace(event.workspaceId(), event.userId());
        if (removed > 0) {
            log.debug(
                    "Removed {} notifications for a departing member: workspaceId={} userId={}",
                    removed,
                    event.workspaceId(),
                    event.userId());
        }
    }

    /**
     * Somebody left one project, and is still in the workspace.
     *
     * <p>Only that project's notifications go. Narrowing on {@code project_id} rather than on the
     * entity is what makes this one statement: a mention on a task in that project carries the
     * project on its own row, so nothing has to be resolved to know it belongs to the project being
     * left.
     */
    @EventListener
    @Transactional
    void onProjectMemberRemoved(ProjectEvents.ProjectMemberRemoved event) {
        int removed = notifications.deleteForMemberOfProject(event.projectId(), event.userId());
        if (removed > 0) {
            log.debug(
                    "Removed {} notifications for a departing project member: projectId={} userId={}",
                    removed,
                    event.projectId(),
                    event.userId());
        }
    }

    /** The account itself was removed, everywhere at once. */
    @EventListener
    @Transactional
    void onUserDeleted(UserDeletedEvent event) {
        int removed = notifications.deleteForUser(event.userId());
        if (removed > 0) {
            log.debug("Removed {} notifications for a removed account: userId={}", removed, event.userId());
        }
    }
}
