package com.company.taskmanagementplatform.tasks;

/**
 * How many tasks hold one status, inside whatever scope was asked about.
 *
 * <p>Counted in SQL by the module that owns the table. Nothing outside this package loads a
 * collection of tasks in order to count it, which is the requirements' own instruction about not
 * fetching records unnecessarily applied to an aggregate.
 *
 * <p>A status with no tasks is absent rather than present at zero. The reports module fills the
 * gaps, because it is the one that knows a chart needs every column drawn.
 */
public record TaskStatusCount(TaskStatus status, long count) {}
