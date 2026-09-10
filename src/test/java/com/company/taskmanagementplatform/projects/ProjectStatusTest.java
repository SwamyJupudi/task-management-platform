package com.company.taskmanagementplatform.projects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The project lifecycle, exercised over the whole matrix rather than the happy path.
 *
 * <p>The approved rules are:
 *
 * <pre>
 *   PLANNING  -> ACTIVE, ARCHIVED
 *   ACTIVE    -> ON_HOLD, COMPLETED, ARCHIVED
 *   ON_HOLD   -> ACTIVE, ARCHIVED
 *   COMPLETED -> ACTIVE, ARCHIVED
 *   ARCHIVED  -> PLANNING, ACTIVE, ON_HOLD, COMPLETED
 * </pre>
 *
 * <p>Every pair is asserted, in both directions, so a transition added or removed by accident fails
 * here rather than being discovered by somebody who could not move a project.
 */
class ProjectStatusTest {

    @Test
    void planningMayOnlyStartOrBeAbandoned() {
        assertThat(ProjectStatus.PLANNING.allowedTargets())
                .containsExactlyInAnyOrder(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED);
    }

    @Test
    void activeMayPauseFinishOrBeArchived() {
        assertThat(ProjectStatus.ACTIVE.allowedTargets())
                .containsExactlyInAnyOrder(ProjectStatus.ON_HOLD, ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED);
    }

    @Test
    void onHoldMayResumeOrBeArchived() {
        assertThat(ProjectStatus.ON_HOLD.allowedTargets())
                .containsExactlyInAnyOrder(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED);
    }

    @Test
    void completedMayReopenOrBeArchived() {
        assertThat(ProjectStatus.COMPLETED.allowedTargets())
                .containsExactlyInAnyOrder(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED);
    }

    @Test
    void archivedMayReturnToAnyWorkingState() {
        // Archiving has to be safe to use, which means it cannot be a dead end.
        assertThat(ProjectStatus.ARCHIVED.allowedTargets())
                .containsExactlyInAnyOrder(
                        ProjectStatus.PLANNING,
                        ProjectStatus.ACTIVE,
                        ProjectStatus.ON_HOLD,
                        ProjectStatus.COMPLETED);
    }

    @Test
    void aProjectMayNeverMoveToWhereItAlreadyIs() {
        // Refused rather than ignored, so a no-op is distinguishable from a real
        // move in the activity log a later phase adds.
        for (ProjectStatus status : ProjectStatus.values()) {
            assertThat(status.canMoveTo(status))
                    .as("%s to itself", status)
                    .isFalse();
        }
    }

    @Test
    void planningNeverSkipsStraightToCompleted() {
        // A plan nobody worked on was abandoned rather than finished, and archiving
        // says that honestly.
        assertThat(ProjectStatus.PLANNING.canMoveTo(ProjectStatus.COMPLETED)).isFalse();
        assertThat(ProjectStatus.PLANNING.canMoveTo(ProjectStatus.ON_HOLD)).isFalse();
    }

    @Test
    void completedNeverGoesBackToPlanningOrOnHoldDirectly() {
        assertThat(ProjectStatus.COMPLETED.canMoveTo(ProjectStatus.PLANNING)).isFalse();
        assertThat(ProjectStatus.COMPLETED.canMoveTo(ProjectStatus.ON_HOLD)).isFalse();
    }

    @Test
    void everyStatusCanReachArchivedOrIsArchived() {
        // Nothing may become impossible to put away.
        for (ProjectStatus status : ProjectStatus.values()) {
            assertThat(status == ProjectStatus.ARCHIVED || status.canMoveTo(ProjectStatus.ARCHIVED))
                    .as("%s can be archived", status)
                    .isTrue();
        }
    }

    @Test
    void everyStatusIsReachableFromSomewhere() {
        // A status nothing can move into is a status no project can ever hold.
        for (ProjectStatus target : ProjectStatus.values()) {
            boolean reachable = false;
            for (ProjectStatus from : ProjectStatus.values()) {
                if (from != target && from.canMoveTo(target)) {
                    reachable = true;
                    break;
                }
            }
            assertThat(reachable).as("something can move to %s", target).isTrue();
        }
    }

    @Test
    void noTransitionNamesAStatusOutsideTheEnum() {
        Set<ProjectStatus> known = Set.of(ProjectStatus.values());

        for (ProjectStatus status : ProjectStatus.values()) {
            assertThat(known).containsAll(status.allowedTargets());
        }
    }
}
