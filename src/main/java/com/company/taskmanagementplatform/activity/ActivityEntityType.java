package com.company.taskmanagementplatform.activity;

/**
 * What kind of thing an audit row is about.
 *
 * <p>Stored as text with a check constraint, like every other enumeration in this schema, so adding
 * a kind is a migration that widens a constraint rather than one that alters a type.
 *
 * <p>{@code WORKSPACE} and {@code TEAM} are here and nothing writes them yet. They are the two kinds
 * the requirements name that this phase does not record, and leaving them out of the constraint
 * would mean widening it in the phase that does.
 */
enum ActivityEntityType {
    WORKSPACE,
    TEAM,
    PROJECT,
    TASK,
    SUBTASK,
    COMMENT,
    ATTACHMENT
}
