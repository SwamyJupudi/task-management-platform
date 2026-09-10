package com.company.taskmanagementplatform.teams;

/**
 * Whether a team is in use.
 *
 * <p>Archiving is not deletion. An archived team keeps its roster and its lead and stays readable;
 * what it loses is the ability to change. Deleting a team is a soft delete and hides it entirely.
 */
public enum TeamStatus {
    ACTIVE,
    ARCHIVED
}
