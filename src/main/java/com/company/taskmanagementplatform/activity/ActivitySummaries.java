package com.company.taskmanagementplatform.activity;

import java.util.Locale;
import java.util.Map;

/**
 * Composes the sentence an audit row reads as.
 *
 * <p>The requirements print lines like "Rahul changed Task #123 from TODO to IN PROGRESS" as
 * examples of what must be recorded. What is recorded is the structured row; this is where it
 * becomes a sentence, at the moment somebody reads it, from the name that person holds now.
 *
 * <p>It is deliberately modest. It names the actor and what they did, and leaves the identity of the
 * thing to the client, which has the entity type and identifier beside the sentence and can render a
 * link where this can only render a word. A renderer that tried to say "Task PROJ-12" would need a
 * lookup per row, which is how a history page becomes N+1.
 *
 * <p>An action with no case here still reads sensibly, because the fallback turns
 * {@code comment.created} into "created a comment". A later phase adding an action therefore gets a
 * reasonable line for free rather than a blank one.
 */
final class ActivitySummaries {

    private ActivitySummaries() {}

    static String render(String action, String actorName, Map<String, Object> metadata) {
        String who = actorName == null || actorName.isBlank() ? "The platform" : actorName;
        return who + " " + phrase(action, metadata) + ".";
    }

    private static String phrase(String action, Map<String, Object> metadata) {
        return switch (action) {
            case ActivityActions.TASK_STATUS_CHANGED, ActivityActions.SUBTASK_STATUS_CHANGED,
                            ActivityActions.PROJECT_STATUS_CHANGED ->
                    "changed the status from " + readable(metadata, "from") + " to " + readable(metadata, "to");
            case ActivityActions.TASK_ASSIGNED ->
                    metadata.get("newAssigneeUserId") == null ? "left it unassigned" : "reassigned it";
            case ActivityActions.PROJECT_OWNER_CHANGED ->
                    metadata.get("newOwnerUserId") == null ? "left it without an owner" : "changed the owner";
            case ActivityActions.PROJECT_MEMBER_ADDED -> "added somebody to the project";
            case ActivityActions.PROJECT_MEMBER_REMOVED -> "removed somebody from the project";
            case ActivityActions.COMMENT_CREATED -> "added a comment";
            case ActivityActions.COMMENT_UPDATED -> "edited a comment";
            case ActivityActions.COMMENT_DELETED -> "removed a comment";
            case ActivityActions.ATTACHMENT_UPLOADED -> "attached " + text(metadata, "filename", "a file");
            case ActivityActions.ATTACHMENT_DELETED -> "removed " + text(metadata, "filename", "a file");
            case ActivityActions.TASK_DEPENDENCY_ADDED -> "made it wait on another task";
            case ActivityActions.TASK_DEPENDENCY_REMOVED -> "removed a dependency";
            default -> fallback(action);
        };
    }

    /** {@code task.created} becomes "created a task", which is right for nearly every action. */
    private static String fallback(String action) {
        int dot = action.indexOf('.');
        if (dot < 0) {
            return action;
        }

        String entity = action.substring(0, dot);
        String what = action.substring(dot + 1).replace('_', ' ');
        return what + " a " + entity;
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
