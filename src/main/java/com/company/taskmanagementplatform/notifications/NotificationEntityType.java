package com.company.taskmanagementplatform.notifications;

/**
 * What a notification points at.
 *
 * <p>Three kinds, not the seven the audit trail has, because a notification is a thing somebody
 * clicks. A mention lives on a comment and a comment lives on a task, but the row a client opens is
 * the comment, so {@code COMMENT} is here and {@code SUBTASK} and {@code ATTACHMENT} are not:
 * nothing in the requirements' list of triggers is about either.
 *
 * <p>Stored as text with a check constraint, like every other enumeration in this schema.
 */
enum NotificationEntityType {
    PROJECT,
    TASK,
    COMMENT
}
