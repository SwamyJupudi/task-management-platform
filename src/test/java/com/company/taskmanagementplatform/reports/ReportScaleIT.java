package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;

/**
 * The test the facade design exists to make possible: that a dashboard costs a fixed number of
 * queries however much work it is describing.
 *
 * <p>An N+1 in a report is not a correctness bug and never fails a test that only checks the numbers.
 * It is a feature that works perfectly on the fixture somebody wrote it against and falls over the
 * first time a real workspace opens it. So this asserts the shape of the work rather than the time it
 * takes: query counts are deterministic, where a timing assertion on a container would be a test that
 * fails on a slow machine and gets deleted.
 *
 * <p>The bound is checked by doubling the data and asserting the count does not move. That is
 * stronger than a fixed number and does not have to be edited every time a panel is added.
 */
class ReportScaleIT extends ReportApiTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /**
     * The two threads that write after somebody else's transaction commits.
     *
     * <p>Held only so a measurement can wait for them. Hibernate's statistics are counted per
     * session factory rather than per thread, so a row either of these writes lands in the same
     * counter the measurement is reading, and the fixture that set a test up is exactly what fills
     * their queues.
     */
    @Autowired
    @Qualifier("activityExecutor")
    private ThreadPoolTaskExecutor activityExecutor;

    @Autowired
    @Qualifier("notificationExecutor")
    private ThreadPoolTaskExecutor notificationExecutor;

    @Test
    void theAdministratorsDashboardCostsTheSameOverTwiceTheWork() throws Exception {
        Scenario scenario = scenario();
        seed(scenario, 3, 10);

        long small = queriesFor(workspaceDashboard(scenario), scenario.adminId());

        seed(scenario, 3, 10);

        long large = queriesFor(workspaceDashboard(scenario), scenario.adminId());

        assertThat(large).as("the workspace dashboard must not cost more as the workspace grows").isEqualTo(small);
    }

    @Test
    void theEmployeeDashboardCostsTheSameOverTwiceTheWork() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        for (int i = 0; i < 10; i++) {
            taskDue(scenario, "Mine " + i, employee, LocalDate.now().plusDays(1));
        }

        long small = queriesFor(myDashboard(scenario), employee);

        for (int i = 0; i < 10; i++) {
            taskDue(scenario, "Mine again " + i, employee, LocalDate.now().plusDays(1));
        }

        assertThat(queriesFor(myDashboard(scenario), employee)).isEqualTo(small);
    }

    @Test
    void theOverdueListingCostsTheSameOverTwiceThePageWidth() throws Exception {
        // Project keys and assignee names are resolved for a whole page in one
        // lookup each. A lookup per row is the shape that makes a listing quietly
        // quadratic in the page size.
        // Both measurements fill the page. Spring Data skips the count query when a
        // first page comes back shorter than the size asked for, so comparing a
        // partial page against a full one would differ by that query and say
        // nothing about N+1.
        Scenario scenario = scenario();
        seedOverdue(scenario, 6);

        long small = queriesFor(reportPath(scenario, "/tasks/overdue") + "?size=5", scenario.adminId());

        seedOverdue(scenario, 6);

        assertThat(queriesFor(reportPath(scenario, "/tasks/overdue") + "?size=5", scenario.adminId()))
                .isEqualTo(small);
    }

    @Test
    void theTeamPerformancePanelCostsTheSameForOneTeamAsForSeveral() throws Exception {
        Scenario scenario = scenario();
        addTeamWithWork(scenario);

        long one = queriesFor(workspaceDashboard(scenario), scenario.adminId());

        addTeamWithWork(scenario);
        addTeamWithWork(scenario);

        assertThat(queriesFor(workspaceDashboard(scenario), scenario.adminId())).isEqualTo(one);
    }

    @Test
    void noReportEndpointReturnsAnUnboundedCollection() throws Exception {
        // Every listing pages, the dashboard panels are capped, and the trend is
        // bounded by the window maximum. This walks each of them with more rows than
        // any cap allows and asserts nothing came back unbounded.
        Scenario scenario = scenario();
        seed(scenario, 2, 12);

        assertThat(sizeOf(reportPath(scenario, "/tasks/overdue"), scenario.adminId(), "content"))
                .isLessThanOrEqualTo(20);
        assertThat(sizeOf(reportPath(scenario, "/workload"), scenario.adminId(), "content"))
                .isLessThanOrEqualTo(20);
        assertThat(sizeOf(reportPath(scenario, "/projects"), scenario.adminId(), "content"))
                .isLessThanOrEqualTo(20);
        assertThat(sizeOf(workspaceDashboard(scenario), scenario.adminId(), "projectProgress"))
                .isLessThanOrEqualTo(20);
        assertThat(sizeOf(workspaceDashboard(scenario), scenario.adminId(), "teamPerformance"))
                .isLessThanOrEqualTo(20);
    }

    // --- helpers ------------------------------------------------------------

    private long queriesFor(String path, UUID callerId) throws Exception {
        settle();

        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(callerId)))
                .andExpect(status().isOk());

        return statistics.getPrepareStatementCount();
    }

    /**
     * Waits until the audit and notification threads have nothing left to write.
     *
     * <p>Without this the test is a race rather than a measurement. Building a fixture creates tasks
     * and assigns them, which queues an activity row and a notification row per action, and both are
     * written after the creating transaction commits on a thread of their own. Hibernate counts
     * prepared statements per session factory, not per thread, so one of those writes landing between
     * {@code statistics.clear()} and the end of the measured request is counted as if the request had
     * made it. The count then comes out one or two high, at random, and the assertion fails on
     * whichever measurement happened to catch a straggler rather than on an N+1.
     *
     * <p>This is the same fact {@code AbstractCollaborationIT.eventually} exists for, applied to the
     * opposite need: that test waits for a row to appear, this one waits for the writing to stop.
     * Waiting rather than sleeping, so it costs nothing on a machine that had already finished.
     */
    private void settle() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();

        while (System.nanoTime() < deadline) {
            // Twice, a moment apart. One writer can queue work for the other, and a
            // worker that has taken a task off its queue is briefly neither queued
            // nor yet counted as active, so a single look can catch a lull that is
            // not the end.
            if (quiet() && quiet()) {
                return;
            }
            Thread.sleep(10);
        }

        throw new AssertionError("The background writers were still busy after 10s; the count would be a race");
    }

    /** Both writers holding nothing, observed after a short pause so a lull is not mistaken for the end. */
    private boolean quiet() throws InterruptedException {
        Thread.sleep(20);
        return idle(activityExecutor) && idle(notificationExecutor);
    }

    private static boolean idle(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return pool.getQueue().isEmpty() && pool.getActiveCount() == 0;
    }

    private int sizeOf(String path, UUID callerId, String field) throws Exception {
        String body = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(callerId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(body).get(field).size();
    }

    private void seed(Scenario scenario, int projects, int tasksEach) {
        for (int p = 0; p < projects; p++) {
            ProjectResponse project = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
            for (int t = 0; t < tasksEach; t++) {
                taskDue(scenario, project.id(), "Task " + t, null, LocalDate.now().minusDays(t % 5 + 1));
            }
        }
    }

    private void seedOverdue(Scenario scenario, int count) {
        UUID person = projectMember(scenario, "EMPLOYEE");
        for (int i = 0; i < count; i++) {
            taskDue(scenario, "Late " + UUID.randomUUID(), person, LocalDate.now().minusDays(i + 1));
        }
    }

    private void addTeamWithWork(Scenario scenario) {
        UUID lead = member(scenario, "TEAM_LEAD");
        UUID team = taskFixtures
                .team(scenario.workspaceId(), uniqueTeamName(), lead, scenario.adminId())
                .id();
        ProjectResponse project =
                taskFixtures.project(scenario.workspaceId(), uniqueKey(), null, team, scenario.adminId());
        taskDue(scenario, project.id(), "Team work", null, LocalDate.now().minusDays(1));
    }
}
