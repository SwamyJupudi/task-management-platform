package com.company.taskmanagementplatform.tasks;

import java.util.Set;

/**
 * Where a task is in its life, and which moves are legal from here.
 *
 * <p>The requirements print these four under <em>Task Views</em> as the columns of a Kanban board,
 * not as a workflow. A strictly linear reading would forbid sending work back from review to in
 * progress, which is the most common real move there is, and would make a card undraggable
 * leftwards on the very board the requirements ask for. So this is a matrix, recorded in {@code
 * architecture.md} and approved, and it is permissive on purpose.
 *
 * <p>Nothing is a dead end. A finished task reopens to any working state, which is what makes
 * completing one safe: somebody who marks the wrong task done can simply move it back.
 *
 * <p>Finishing straight from TODO is allowed, and that is a deliberate departure from the project
 * lifecycle, where a plan cannot jump to completed because a plan nobody worked on was abandoned
 * rather than finished. A task is smaller than a project. A chore that needed no visible work and no
 * review is an ordinary thing to tick off, and refusing it would only teach people to move the card
 * twice.
 *
 * <p>What is still refused is the pair that would mean nothing: moving to or from review without
 * passing through in progress. Review is a statement about work that exists, so a task can reach it
 * only from somebody having started, and a rejected review sends the work back to be done rather
 * than back to the backlog.
 *
 * <p>A transition to the status a task already holds is refused rather than ignored. Silently
 * accepting it would make a no-op indistinguishable from a real move in the activity log that phase
 * six adds.
 *
 * <p>Subtasks share this enum and this matrix. They are the same four columns on the same board, and
 * a second state machine with the same states would be a second thing to keep in step.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    REVIEW,
    DONE;

    /** True when the work is finished, which is what a subtask calls "completed". */
    public boolean isComplete() {
        return this == DONE;
    }

    public boolean canMoveTo(TaskStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<TaskStatus> allowedTargets() {
        return switch (this) {
            case TODO -> Set.of(IN_PROGRESS, DONE);
            case IN_PROGRESS -> Set.of(TODO, REVIEW, DONE);
            case REVIEW -> Set.of(IN_PROGRESS, DONE);
            case DONE -> Set.of(TODO, IN_PROGRESS, REVIEW);
        };
    }
}
