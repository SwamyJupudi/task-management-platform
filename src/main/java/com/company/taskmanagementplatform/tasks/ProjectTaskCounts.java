package com.company.taskmanagementplatform.tasks;

import java.util.UUID;

/**
 * One project's task numbers, for the row that reports its completion beside its progress.
 *
 * <p>Progress itself is not here. It is a derived column the projects module owns and phase eight
 * reads rather than recomputes, so that there is one rule for it rather than two.
 */
public record ProjectTaskCounts(UUID projectId, long total, long done, long overdue) {

    static ProjectTaskCounts empty(UUID projectId) {
        return new ProjectTaskCounts(projectId, 0, 0, 0);
    }
}
