package com.company.taskmanagementplatform.subtasks;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.tasks.TaskStatus;

/**
 * What the subtasks module publishes to {@code reports}, and nothing more.
 *
 * <p>Exactly one question, because exactly one figure in phase eight needs this table: the employee
 * dashboard shows how many checklist items are still open against this person's name, which is work
 * they hold that the task counts do not show. Nothing else in the phase reads subtasks, and adding
 * methods nothing calls would be dead code in a module whose whole point is a narrow surface.
 *
 * <p>Narrowed to the assignee rather than to a project scope. The assignee is a foreign key into
 * {@code project_members}, so a subtask cannot be assigned to somebody outside the project that
 * holds it, and "assigned to me" is a subset of "visible to me" by construction rather than by a
 * check. That is the same property that makes My Tasks safe one level up.
 *
 * <p><strong>Read-only.</strong> Counted in SQL, never by loading rows and adding them up.
 */
@Service
public class SubtaskAnalyticsFacade {

    private final SubtaskRepository subtasks;

    SubtaskAnalyticsFacade(SubtaskRepository subtasks) {
        this.subtasks = subtasks;
    }

    /** How many live checklist items are assigned to this person and not yet finished. */
    @Transactional(readOnly = true)
    public long openCountFor(UUID workspaceId, UUID assigneeUserId) {
        return subtasks.countByWorkspaceIdAndAssigneeUserIdAndStatusNotAndDeletedAtIsNull(
                workspaceId, assigneeUserId, TaskStatus.DONE);
    }
}
