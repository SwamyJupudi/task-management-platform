package com.company.taskmanagementplatform.notifications;

import java.util.Locale;
import java.util.Map;

/**
 * Composes the sentence a notification reads as.
 *
 * <p>Nothing of the sort is stored, for the reason the audit trail gives: a sentence written into the
 * table would still say "Ada Lovelace" a year after Ada changed her name. The row holds identifiers
 * and the message is built at the moment somebody reads it, from the names those identifiers resolve
 * to then.
 *
 * <p>The task's title is passed in rather than looked up here, and may be absent. It is absent for
 * two different reasons that read the same way to this class and must: the task has been removed, or
 * the recipient can no longer see the project it lives in. Both produce a message that says what
 * happened without naming the thing it happened to.
 *
 * <p>An unknown type still reads sensibly rather than blankly, which matters during a rolling deploy
 * when one instance writes a code another does not yet know.
 */
final class NotificationMessages {

    private NotificationMessages() {}

    static String render(String type, String actorName, String taskTitle, Map<String, Object> metadata) {
        String who = actorName == null || actorName.isBlank() ? "The platform" : actorName;
        String what = taskTitle == null || taskTitle.isBlank() ? null : taskTitle;

        return switch (type) {
            case "task.assigned" -> what == null ? who + " assigned a task to you." : who + " assigned " + what + " to you.";
            case "task.status_changed" ->
                who + " moved " + or(what, "a task") + " from " + readable(metadata, "from") + " to "
                        + readable(metadata, "to") + ".";
            case "task.deadline_approaching" -> deadline(what, metadata);
            case "comment.created" -> who + " commented on " + or(what, "a task") + ".";
            case "comment.mentioned" -> who + " mentioned you on " + or(what, "a task") + ".";
            case "project.member_added" -> who + " added you to a project.";
            case "project.status_changed" ->
                who + " changed the project status from " + readable(metadata, "from") + " to "
                        + readable(metadata, "to") + ".";
            default -> who + " " + type.replace('.', ' ').replace('_', ' ') + ".";
        };
    }

    /**
     * The one message with no actor, and the one that counts.
     *
     * <p>"Due today" and "due tomorrow" are worth saying plainly; beyond that the number of days is
     * more useful than a date somebody has to subtract from today themselves.
     */
    private static String deadline(String taskTitle, Map<String, Object> metadata) {
        String subject = or(taskTitle, "A task");
        Object remaining = metadata.get("daysRemaining");
        int days = remaining instanceof Number number ? number.intValue() : -1;

        return switch (days) {
            case 0 -> subject + " is due today.";
            case 1 -> subject + " is due tomorrow.";
            default -> days > 1
                    ? subject + " is due in " + days + " days."
                    : subject + " is due on " + text(metadata, "dueDate", "an unknown date") + ".";
        };
    }

    private static String or(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String readable(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "nothing" : value.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String text(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        return value == null ? fallback : value.toString();
    }
}
