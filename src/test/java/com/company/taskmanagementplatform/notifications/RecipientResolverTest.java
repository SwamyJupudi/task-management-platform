package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The three rules that decide who hears about something.
 *
 * <p>No Spring context and no database: the rules are static and take identifiers, which is the
 * whole reason they were written that way. Everything here would otherwise need a container to
 * assert a set difference.
 */
class RecipientResolverTest {

    private static final UUID ADA = UUID.randomUUID();
    private static final UUID GRACE = UUID.randomUUID();
    private static final UUID ALAN = UUID.randomUUID();

    @Test
    void theActorIsNeverToldWhatTheyJustDid() {
        List<UUID> recipients = RecipientResolver.exclude(List.of(ADA, GRACE), ADA, Set.of());

        assertThat(recipients).containsExactly(GRACE);
    }

    @Test
    void beingBothAssigneeAndReporterEarnsOneNotification() {
        List<UUID> recipients = RecipientResolver.exclude(List.of(GRACE, GRACE), ADA, Set.of());

        assertThat(recipients).containsExactly(GRACE);
    }

    @Test
    void somebodyAlreadyMentionedDoesNotAlsoHearAboutTheComment() {
        // Rule three. One action, one notification per person: a mention beats the
        // comment it is in, because they are the same fact told twice.
        List<UUID> recipients = RecipientResolver.exclude(List.of(GRACE, ALAN), ADA, Set.of(GRACE));

        assertThat(recipients).containsExactly(ALAN);
    }

    @Test
    void aNullCandidateIsDroppedRatherThanRejected() {
        // An unassigned task has a null assignee, which is ordinary rather than exceptional.
        List<UUID> recipients = RecipientResolver.exclude(Arrays.asList(null, GRACE), ADA, Set.of());

        assertThat(recipients).containsExactly(GRACE);
    }

    @Test
    void anActionWithNoActorStillReachesEverybody() {
        // The deadline scan has no actor, so nothing is excluded by rule one.
        List<UUID> recipients = RecipientResolver.exclude(List.of(ADA, GRACE), null, Set.of());

        assertThat(recipients).containsExactly(ADA, GRACE);
    }

    @Test
    void orderIsPreservedSoRowsAreWrittenPredictably() {
        List<UUID> recipients = RecipientResolver.exclude(List.of(GRACE, ALAN, ADA), null, Set.of());

        assertThat(recipients).containsExactly(GRACE, ALAN, ADA);
    }

    @Test
    void nobodyIsLeftWhenTheOnlyCandidateIsTheActor() {
        assertThat(RecipientResolver.exclude(List.of(ADA), ADA, Set.of())).isEmpty();
    }

    @Test
    void aSingleRecipientRunsThroughTheSameRules() {
        assertThat(RecipientResolver.only(GRACE, ADA)).containsExactly(GRACE);
        assertThat(RecipientResolver.only(ADA, ADA)).isEmpty();
        assertThat(RecipientResolver.only(null, ADA)).isEmpty();
    }

    @Test
    void unassigningATaskTellsNobody() {
        // TaskAssigned with a null new assignee is how unassignment is announced.
        assertThat(RecipientResolver.only(null, ADA)).isEmpty();
    }
}
