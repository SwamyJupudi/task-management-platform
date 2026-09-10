package com.company.taskmanagementplatform.projects;

import java.util.Set;

/**
 * Where a project is in its life, and which moves are legal from here.
 *
 * <p>The requirements print the five as a chain and state no transition rules, so the matrix below
 * is our reading, recorded in {@code architecture.md} and approved. Two properties of it are worth
 * naming. Nothing is a dead end: an archived project can be brought back to any working state, which
 * is what makes archiving safe to use. And nothing skips the middle: a project cannot jump from
 * planning straight to completed, because a plan that was never worked on was abandoned rather than
 * finished, and archiving says that honestly.
 *
 * <p>A transition to the status a project already holds is refused rather than ignored. Silently
 * accepting it would make a no-op indistinguishable from a real move in the activity log that phase
 * six adds.
 */
public enum ProjectStatus {
    PLANNING,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    ARCHIVED;

    boolean canMoveTo(ProjectStatus target) {
        return allowedTargets().contains(target);
    }

    Set<ProjectStatus> allowedTargets() {
        return switch (this) {
            case PLANNING -> Set.of(ACTIVE, ARCHIVED);
            case ACTIVE -> Set.of(ON_HOLD, COMPLETED, ARCHIVED);
            case ON_HOLD -> Set.of(ACTIVE, ARCHIVED);
            case COMPLETED -> Set.of(ACTIVE, ARCHIVED);
            case ARCHIVED -> Set.of(PLANNING, ACTIVE, ON_HOLD, COMPLETED);
        };
    }
}
