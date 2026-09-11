package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The sentence each type reads as, including the degraded form.
 *
 * <p>The degraded cases are the ones worth having a test for. A message that quietly kept naming a
 * task after its reader lost access to it would be a leak that no authorization test would catch,
 * because nothing was fetched that should not have been: the name was already in the feed.
 */
class NotificationMessagesTest {

    @Test
    void anAssignmentNamesTheTaskAndThePersonWhoDidIt() {
        String message = NotificationMessages.render("task.assigned", "Ada Lovelace", "Build login", Map.of());

        assertThat(message).isEqualTo("Ada Lovelace assigned Build login to you.");
    }

    @Test
    void aStatusChangeReadsBothStatusesInPlainWords() {
        String message = NotificationMessages.render(
                "task.status_changed", "Ada Lovelace", "Build login", Map.of("from", "TODO", "to", "IN_PROGRESS"));

        assertThat(message).isEqualTo("Ada Lovelace moved Build login from todo to in progress.");
    }

    @Test
    void aMentionSaysWhoNamedYouAndWhere() {
        String message = NotificationMessages.render("comment.mentioned", "Grace Hopper", "Build login", Map.of());

        assertThat(message).isEqualTo("Grace Hopper mentioned you on Build login.");
    }

    @Test
    void aDeadlineDueTodaySaysSo() {
        String message = NotificationMessages.render(
                "task.deadline_approaching", null, "Build login", Map.of("daysRemaining", 0, "dueDate", "2026-03-09"));

        assertThat(message).isEqualTo("Build login is due today.");
    }

    @Test
    void aDeadlineTomorrowIsNamedRatherThanCounted() {
        String message = NotificationMessages.render(
                "task.deadline_approaching", null, "Build login", Map.of("daysRemaining", 1, "dueDate", "2026-03-10"));

        assertThat(message).isEqualTo("Build login is due tomorrow.");
    }

    @Test
    void aDeadlineFurtherOutCountsTheDays() {
        String message = NotificationMessages.render(
                "task.deadline_approaching", null, "Build login", Map.of("daysRemaining", 3, "dueDate", "2026-03-12"));

        assertThat(message).isEqualTo("Build login is due in 3 days.");
    }

    @Test
    void aTaskTheReaderCanNoLongerSeeIsNotNamed() {
        // The title arrives null when the reader has lost access to the project, and
        // the message has to say what happened without saying what it happened to.
        String message = NotificationMessages.render(
                "comment.created", "Ada Lovelace", null, Map.of());

        assertThat(message).isEqualTo("Ada Lovelace commented on a task.");
    }

    @Test
    void aDeadlineWithNoVisibleTaskStillReadsAsASentence() {
        String message = NotificationMessages.render(
                "task.deadline_approaching", null, null, Map.of("daysRemaining", 2, "dueDate", "2026-03-11"));

        assertThat(message).isEqualTo("A task is due in 2 days.");
    }

    @Test
    void anActionByThePlatformHasNoNameToUse() {
        String message = NotificationMessages.render("task.status_changed", null, "Build login", Map.of());

        assertThat(message).startsWith("The platform moved Build login");
    }

    @Test
    void anUnknownTypeStillReadsSensibly() {
        // A rolling deploy can have one instance writing a code another does not know.
        String message = NotificationMessages.render("task.frobnicated", "Ada Lovelace", null, Map.of());

        assertThat(message).isEqualTo("Ada Lovelace task frobnicated.");
    }

    @Test
    void aMissingStatusReadsAsNothingRatherThanAsNull() {
        String message = NotificationMessages.render("project.status_changed", "Ada Lovelace", null, Map.of());

        assertThat(message).isEqualTo("Ada Lovelace changed the project status from nothing to nothing.");
    }
}
