package com.company.taskmanagementplatform.projects;

/**
 * How many projects hold one status, inside whatever scope was asked about.
 *
 * <p>Counted in SQL by the module that owns the table. Nothing outside this package loads a
 * collection of projects in order to count it, which is the requirements' own instruction about not
 * fetching records unnecessarily applied to an aggregate.
 */
public record ProjectStatusCount(ProjectStatus status, long count) {}
