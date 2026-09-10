package com.company.taskmanagementplatform.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The whole transition matrix, rather than a happy path through it.
 *
 * <p>The matrix is our reading of a requirements document that prints the four statuses as board
 * columns and states no rules at all, so the properties below are the decisions worth defending, and
 * this is where they are written down as executable claims rather than as prose.
 */
class TaskStatusTest {

    @Test
    void workBeginsAndCanBeFinishedStraightAway() {
        // Finishing from TODO is allowed on purpose, unlike a project, which cannot
        // jump from planning to completed. A chore that needed no visible work is an
        // ordinary thing to tick off, and refusing it teaches people to click twice.
        assertThat(TaskStatus.TODO.canMoveTo(TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStatus.TODO.canMoveTo(TaskStatus.DONE)).isTrue();
    }

    @Test
    void reviewIsReachableOnlyFromWorkThatHasStarted() {
        // Review is a statement about work that exists, so nothing arrives there
        // from the backlog, and a rejected review goes back to being done rather
        // than back to being listed.
        assertThat(TaskStatus.TODO.canMoveTo(TaskStatus.REVIEW)).isFalse();
        assertThat(TaskStatus.IN_PROGRESS.canMoveTo(TaskStatus.REVIEW)).isTrue();
        assertThat(TaskStatus.REVIEW.canMoveTo(TaskStatus.TODO)).isFalse();
        assertThat(TaskStatus.REVIEW.canMoveTo(TaskStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void workCanBeSentBackFromInProgress() {
        // The move a strictly linear reading would forbid, and the reason the matrix
        // exists at all: a card has to be draggable leftwards on the board the
        // requirements ask for.
        assertThat(TaskStatus.IN_PROGRESS.canMoveTo(TaskStatus.TODO)).isTrue();
    }

    @Test
    void nothingIsADeadEnd() {
        // What makes finishing a task safe. Somebody who marks the wrong one done
        // can put it back without an administrator.
        for (TaskStatus status : TaskStatus.values()) {
            assertThat(status.allowedTargets())
                    .as("targets reachable from %s", status)
                    .isNotEmpty();
        }
    }

    @Test
    void everyStatusIsReachableFromSomewhere() {
        // A status nothing can move to is a status nothing can ever hold, which
        // would make it a column on the board that stays permanently empty.
        Set<TaskStatus> reachable = EnumSet.noneOf(TaskStatus.class);
        Arrays.stream(TaskStatus.values()).forEach(status -> reachable.addAll(status.allowedTargets()));

        assertThat(reachable).containsExactlyInAnyOrder(TaskStatus.values());
    }

    @Test
    void aFinishedTaskReopensToEveryWorkingState() {
        assertThat(TaskStatus.DONE.allowedTargets())
                .containsExactlyInAnyOrder(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REVIEW);
    }

    @Test
    void noStatusListsItselfAsATarget() {
        // The service refuses a move to the status a task already holds, and it
        // answers 409 rather than ignoring it, so that a no-op never looks like a
        // real transition in the activity log. The matrix must agree.
        for (TaskStatus status : TaskStatus.values()) {
            assertThat(status.allowedTargets()).as("targets of %s", status).doesNotContain(status);
        }
    }

    @Test
    void onlyDoneCountsAsComplete() {
        // The whole of what a subtask calls completion, and one half of the project
        // progress rule.
        assertThat(TaskStatus.DONE.isComplete()).isTrue();
        assertThat(TaskStatus.TODO.isComplete()).isFalse();
        assertThat(TaskStatus.IN_PROGRESS.isComplete()).isFalse();
        assertThat(TaskStatus.REVIEW.isComplete()).isFalse();
    }
}
