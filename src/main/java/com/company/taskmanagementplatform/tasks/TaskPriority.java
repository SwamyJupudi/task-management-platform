package com.company.taskmanagementplatform.tasks;

/**
 * The four levels the requirements name for a task.
 *
 * <p>The same four values as {@code ProjectPriority}, and deliberately a separate type rather than
 * an import of it. Sharing the enum would make this module depend on {@code projects} for a value,
 * and would mean that changing what priorities a project may hold silently changed what priorities a
 * task may hold. They agree today because the requirements say so, not because one is defined in
 * terms of the other.
 */
public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
