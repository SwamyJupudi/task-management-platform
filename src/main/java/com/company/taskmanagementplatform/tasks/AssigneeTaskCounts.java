package com.company.taskmanagementplatform.tasks;

import java.util.UUID;

/**
 * One person's workload, as the requirements' "team / employee workload" figure.
 *
 * <p>Effort is summed over open work only. A person's load is what is still on their desk; adding
 * the minutes of everything they have ever finished would make the number grow forever and would
 * stop describing anything.
 *
 * <p>{@code completedInPeriod} is the one figure here bounded by a window rather than by a status,
 * because "how much did they finish" is meaningless without saying over what stretch of time.
 */
public record AssigneeTaskCounts(
        UUID userId,
        long open,
        long inProgress,
        long overdue,
        long completedInPeriod,
        long estimatedMinutes,
        long actualMinutes) {}
