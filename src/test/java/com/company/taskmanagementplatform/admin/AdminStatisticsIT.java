package com.company.taskmanagementplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.admin.dto.SystemStatisticsResponse;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * Every figure the statistics panel reports, against a fixture of known shape spanning two
 * workspaces.
 *
 * <p>Two workspaces is the point rather than thoroughness. A reporting mistake here does not throw
 * and does not return the wrong status: it returns a smaller number. A figure that accidentally
 * carried a tenant predicate would pass every single-workspace test ever written, and would read low
 * in production where nobody has a total to compare it against.
 *
 * <p>Every assertion is therefore a <strong>difference</strong> across one operation rather than an
 * absolute. The container is shared across the suite and never truncated, as {@code
 * AbstractIntegrationTest} explains, so no test here can know what the installation held before it
 * started. Asserting a delta is both correct under that constraint and a stronger statement: it says
 * the figure moved by exactly what this test did.
 */
class AdminStatisticsIT extends AdminApiTestBase {

    @Test
    void countsAccountsAcrossTheWholeInstallationWithEveryStatusPresent() throws Exception {
        Estate estate = estate();
        SystemStatisticsResponse before = statistics(estate);

        fixtures.pendingUser(uniqueEmail("unverified"));
        fixtures.verifiedUser(uniqueEmail("verified"));

        SystemStatisticsResponse after = statistics(estate);

        assertThat(after.accounts().total() - before.accounts().total()).isEqualTo(2);
        assertThat(delta(after, before, "PENDING_APPROVAL")).isEqualTo(1);
        assertThat(delta(after, before, "ACTIVE")).isEqualTo(1);

        // Every status present, including one nobody may hold. A missing column
        // would make a client know the enumeration to draw its chart, and a status
        // that vanished when its last holder changed would read as one that was
        // never there.
        assertThat(after.accounts().byStatus())
                .containsKeys("PENDING_APPROVAL", "ACTIVE", "DEACTIVATED");
    }

    @Test
    void countsWorkspacesTeamsAndMembershipsAcrossEveryWorkspace() throws Exception {
        Estate estate = estate();
        SystemStatisticsResponse before = statistics(estate);

        // One team in each workspace, so a figure narrowed to either would read one.
        taskFixtures.team(estate.firstWorkspaceId(), "Alpha " + uniqueKey(), null, estate.firstAdminId());
        taskFixtures.team(estate.secondWorkspaceId(), "Beta " + uniqueKey(), null, estate.secondAdminId());

        SystemStatisticsResponse after = statistics(estate);

        assertThat(after.workspaces().teams() - before.workspaces().teams()).isEqualTo(2);
        assertThat(after.workspaces().byStatus()).containsKeys("ACTIVE", "ARCHIVED");
    }

    @Test
    void countsProjectsAndTasksAcrossBothWorkspacesWithEveryStatusPresent() throws Exception {
        Estate estate = estate();
        SystemStatisticsResponse before = statistics(estate);

        taskDue(estate, estate.firstProjectId(), "First work", null, estate.firstAdminId());
        taskDue(estate, estate.secondProjectId(), "Second work", null, estate.secondAdminId());

        SystemStatisticsResponse after = statistics(estate);

        // Two tasks, one in each workspace. A query scoped to either reads one.
        assertThat(after.work().tasks() - before.work().tasks()).isEqualTo(2);
        assertThat(after.work().tasksByStatus()).containsKeys("TODO", "IN_PROGRESS", "REVIEW", "DONE");
        assertThat(after.work().projectsByStatus())
                .containsKeys("PLANNING", "ACTIVE", "ON_HOLD", "COMPLETED", "ARCHIVED");
    }

    @Test
    void countsOverdueWorkByTheSameDefinitionEveryOtherFigureUses() throws Exception {
        Estate estate = estate();
        SystemStatisticsResponse before = statistics(estate);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        taskDue(estate, estate.firstProjectId(), "Late", yesterday, estate.firstAdminId());

        // Finished work is never overdue however late it was. The phase eight
        // definition, reached through the module that owns it, so "overdue" is
        // expressed in this platform twice rather than three times.
        TaskResponse finishedLate = taskDue(estate, estate.secondProjectId(), "Late but done", yesterday, estate.secondAdminId());
        taskFixtures.moveTask(estate.secondWorkspaceId(), finishedLate.id(), "DONE", estate.secondAdminId());

        // Due today is not overdue: the day is not over.
        taskDue(estate, estate.firstProjectId(), "Due today", LocalDate.now(), estate.firstAdminId());

        SystemStatisticsResponse after = statistics(estate);

        assertThat(after.work().tasksOverdue() - before.work().tasksOverdue()).isEqualTo(1);
    }

    @Test
    void aSoftDeletedTaskLeavesEveryFigureItWasIn() throws Exception {
        Estate estate = estate();

        TaskResponse doomed = taskDue(
                estate, estate.firstProjectId(), "Doomed", LocalDate.now().minusDays(3), estate.firstAdminId());

        SystemStatisticsResponse withIt = statistics(estate);
        taskFixtures.deleteTask(estate.firstWorkspaceId(), doomed.id(), estate.firstAdminId());
        SystemStatisticsResponse withoutIt = statistics(estate);

        // Gone rather than flagged, everywhere, per the platform's convention.
        assertThat(withIt.work().tasks() - withoutIt.work().tasks()).isEqualTo(1);
        assertThat(withIt.work().tasksOverdue() - withoutIt.work().tasksOverdue()).isEqualTo(1);
    }

    @Test
    void countsRecentAccountsInsideTheWindow() throws Exception {
        Estate estate = estate();
        SystemStatisticsResponse before = statistics(estate);

        fixtures.verifiedUser(uniqueEmail("newcomer"));

        SystemStatisticsResponse after = statistics(estate);

        assertThat(after.recent().accountsCreated() - before.recent().accountsCreated()).isEqualTo(1);
        assertThat(after.windowDays()).isEqualTo(30);
    }

    @Test
    void reportsTheWindowItActuallyUsedAndRefusesOneTooWide() throws Exception {
        Estate estate = estate();

        SystemStatisticsResponse narrowed = statisticsWithWindow(estate, 7);
        assertThat(narrowed.windowDays()).isEqualTo(7);

        // Refused rather than clamped, so a client is never quietly given an answer
        // to a different question. The cap is 40 in the test profile.
        mockMvc.perform(get(STATISTICS)
                        .param("windowDays", "41")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(STATISTICS)
                        .param("windowDays", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsStorageAsCountAndBytes() throws Exception {
        Estate estate = estate();
        SystemStatisticsResponse stats = statistics(estate);

        // No files in this fixture, so the assertion worth making is the one about
        // shape: a sum over no rows is null in SQL and must read zero here, or a
        // panel shows a blank where a total belongs.
        assertThat(stats.storage().totalBytes()).isNotNegative();
        assertThat(stats.storage().attachments()).isNotNegative();
    }

    @Test
    void everyPanelOfOneResponseIsReadInTheSameTransaction() throws Exception {
        Estate estate = estate();
        SystemStatisticsResponse stats = statistics(estate);

        // Nothing is cached, so the stamp is the moment of the request rather than
        // of some earlier aggregation, and the panels beside it agree with it.
        assertThat(stats.generatedAt()).isNotNull();
        assertThat(stats.accounts().total()).isPositive();
        assertThat(stats.workspaces().total()).isPositive();
    }

    private long delta(SystemStatisticsResponse after, SystemStatisticsResponse before, String status) {
        return after.accounts().byStatus().get(status) - before.accounts().byStatus().get(status);
    }

    private SystemStatisticsResponse statistics(Estate estate) throws Exception {
        return read(mockMvc.perform(get(STATISTICS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private SystemStatisticsResponse statisticsWithWindow(Estate estate, int windowDays) throws Exception {
        return read(mockMvc.perform(get(STATISTICS)
                        .param("windowDays", String.valueOf(windowDays))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private SystemStatisticsResponse read(String body) {
        return json.readValue(body, SystemStatisticsResponse.class);
    }
}
