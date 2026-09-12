package com.company.taskmanagementplatform.activity;

/**
 * What kind of thing an audit row is about.
 *
 * <p>Stored as text with a check constraint, like every other enumeration in this schema, so adding
 * a kind is a migration that widens a constraint rather than one that alters a type.
 *
 * <p>{@code WORKSPACE} and {@code TEAM} were put here in phase six with nothing writing them, so
 * that a later phase would not have to widen the constraint for them. {@code USER} and {@code ROLE}
 * arrived in {@code V10} for the admin panel, which was that later phase; they are the two kinds an
 * administrative action is about.
 *
 * <p>A row of either new kind carries a null workspace, because account and platform-role
 * administration happens outside any workspace. A {@code ROLE} row is the exception that does carry
 * one, since the mapping from role to permission belongs to a workspace.
 */
enum ActivityEntityType {
    WORKSPACE,
    TEAM,
    PROJECT,
    TASK,
    SUBTASK,
    COMMENT,
    ATTACHMENT,
    USER,
    ROLE
}
