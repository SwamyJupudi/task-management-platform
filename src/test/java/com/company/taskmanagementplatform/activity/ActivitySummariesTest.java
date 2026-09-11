package com.company.taskmanagementplatform.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The sentence an audit row reads as.
 *
 * <p>Composed on read from the name the actor holds now, which is the whole reason no prose is
 * stored in the table.
 */
class ActivitySummariesTest {

    @Test
    void namesTheActorAndBothStatuses() {
        String summary = ActivitySummaries.render(
                ActivityActions.TASK_STATUS_CHANGED, "Rahul Sharma", metadata("from", "TODO", "to", "IN_PROGRESS"));

        assertThat(summary).isEqualTo("Rahul Sharma changed the status from todo to in progress.");
    }

    @Test
    void readsAnAssignmentAndAnUnassignmentDifferently() {
        assertThat(ActivitySummaries.render(
                        ActivityActions.TASK_ASSIGNED, "Srikanth", metadata("newAssigneeUserId", "abc")))
                .isEqualTo("Srikanth reassigned it.");

        assertThat(ActivitySummaries.render(ActivityActions.TASK_ASSIGNED, "Srikanth", Map.of()))
                .isEqualTo("Srikanth left it unassigned.");
    }

    @Test
    void namesTheFileItWasAbout() {
        assertThat(ActivitySummaries.render(
                        ActivityActions.ATTACHMENT_UPLOADED, "Anil", metadata("filename", "design.pdf")))
                .isEqualTo("Anil attached design.pdf.");
    }

    @Test
    void readsACommentTheWayTheRequirementsPrintIt() {
        assertThat(ActivitySummaries.render(ActivityActions.COMMENT_CREATED, "Anil", Map.of()))
                .isEqualTo("Anil added a comment.");
    }

    @Test
    void fallsBackToSomethingSensibleForAnActionItHasNoCaseFor() {
        // A later phase adding an action gets a reasonable line rather than a blank.
        assertThat(ActivitySummaries.render("workspace.archived", "Ada", Map.of()))
                .isEqualTo("Ada archived a workspace.");
    }

    @Test
    void saysThePlatformWhenThereIsNoActor() {
        assertThat(ActivitySummaries.render(ActivityActions.TASK_DELETED, null, Map.of()))
                .isEqualTo("The platform deleted a task.");
    }

    private static Map<String, Object> metadata(Object... keysAndValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            metadata.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return metadata;
    }
}
