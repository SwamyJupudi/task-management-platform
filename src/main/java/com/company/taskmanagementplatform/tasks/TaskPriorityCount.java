package com.company.taskmanagementplatform.tasks;

/** How many tasks hold one priority, inside whatever scope was asked about. */
public record TaskPriorityCount(TaskPriority priority, long count) {}
