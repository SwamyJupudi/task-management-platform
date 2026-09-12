package com.company.taskmanagementplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * Bucketing, gap filling, and the timezone the whole chart is drawn in.
 *
 * <p><strong>This file writes {@code completed_at} in SQL, and that is a deliberate exception.</strong>
 * Every other test in the phase builds its data through the ordinary services, as {@code testing.md}
 * requires. The task service sets {@code completed_at} from {@code Instant.now()} rather than from an
 * injected clock, so no test can otherwise produce a task finished last Tuesday, and a trend that
 * cannot be tested over time is a trend that cannot be tested at all. The alternative was to take a
 * clock into a phase-five service, which is a change to finished work for the sake of a test here.
 * The exception is confined to this class and to that one column.
 */
class ReportTrendsIT extends ReportApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsOnePointPerDayIncludingTheEmptyOnes() throws Exception {
        // A quiet day is a real fact about that day. Leaving it out makes the line
        // jump and invites the client to invent the shape between two points.
        Scenario scenario = scenario();
        LocalDate today = LocalDate.now(zoneOf(scenario));

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(4) + "&to=" + today
                                + "&granularity=DAY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].bucketStart").value(today.minusDays(4).toString()))
                .andExpect(jsonPath("$[4].bucketStart").value(today.toString()))
                .andExpect(jsonPath("$[0].created").value(0))
                .andExpect(jsonPath("$[0].completed").value(0));
    }

    @Test
    void countsTasksOnTheDayTheyWereRaised() throws Exception {
        Scenario scenario = scenario();
        LocalDate today = LocalDate.now(zoneOf(scenario));
        taskDue(scenario, "Raised today", null, null);
        taskDue(scenario, "Also today", null, null);

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today + "&to=" + today + "&granularity=DAY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].created").value(2));
    }

    @Test
    void countsATaskInTheBucketItWasCompletedIn() throws Exception {
        Scenario scenario = scenario();
        ZoneId zone = zoneOf(scenario);
        LocalDate today = LocalDate.now(zone);
        LocalDate threeDaysAgo = today.minusDays(3);

        TaskResponse task = taskDue(scenario, "Finished three days ago", null, null);
        done(scenario, task);
        completedAt(task.id(), threeDaysAgo.atTime(10, 0).atZone(zone).toInstant());

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(5) + "&to=" + today
                                + "&granularity=DAY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bucketStart == '" + threeDaysAgo + "')].completed")
                        .value(1))
                .andExpect(jsonPath("$[?(@.bucketStart == '" + today + "')].completed").value(0));
    }

    @Test
    void bucketsByWeekStartingMonday() throws Exception {
        Scenario scenario = scenario();
        ZoneId zone = zoneOf(scenario);
        LocalDate today = LocalDate.now(zone);

        String body = mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(20) + "&to="
                                + today + "&granularity=WEEK")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var points = json.readTree(body);
        for (var point : points) {
            LocalDate bucket = LocalDate.parse(point.get("bucketStart").asText());
            assertThat(bucket.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        }
        assertThat(points.size()).isBetween(3, 4);
    }

    @Test
    void putsWorkFinishedLateInTheEveningInTheLocalDayRatherThanTheUtcOne() throws Exception {
        // The reason the zone is carried at all. In a workspace nine hours ahead of
        // UTC, 23:00 local is 14:00 the same day in UTC; in one behind it the
        // opposite happens and the evening's work lands on tomorrow's chart.
        Scenario scenario = scenario();
        ZoneId zone = ZoneId.of("Asia/Tokyo");
        setTimezone(scenario, zone);

        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        TaskResponse task = taskDue(scenario, "Finished at eleven", null, null);
        done(scenario, task);
        completedAt(task.id(), yesterday.atTime(23, 0).atZone(zone).toInstant());

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + yesterday.minusDays(1) + "&to="
                                + LocalDate.now(zone) + "&granularity=DAY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bucketStart == '" + yesterday + "')].completed")
                        .value(1));
    }

    @Test
    void choosesDailyForAShortWindowAndWeeklyForALongOne() throws Exception {
        Scenario scenario = scenario();
        LocalDate today = LocalDate.now(zoneOf(scenario));

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(6) + "&to=" + today)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));

        String weekly = mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(34) + "&to="
                                + today)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Thirty-five days is five or six Mondays, never thirty-five points.
        assertThat(json.readTree(weekly).size()).isLessThan(10);
    }

    @Test
    void refusesAnUnknownGranularityAndNamesTheThreeItAccepts() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?granularity=QUARTER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("DAY")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("MONTH")));
    }

    @Test
    void acceptsAGranularityInAnyCase() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?granularity=week")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());
    }

    @Test
    void refusesAnInvertedWindow() throws Exception {
        Scenario scenario = scenario();
        LocalDate today = LocalDate.now();

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today + "&to=" + today.minusDays(5))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAWindowOverTheMaximum() throws Exception {
        // Forty days in the test profile. Without this the trend is the one endpoint
        // that could be asked for an unbounded amount of work.
        Scenario scenario = scenario();
        LocalDate today = LocalDate.now();

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(50) + "&to=" + today)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leavesATaskOutOfTheHistoricalBucketOnceItIsReopened() throws Exception {
        // The caveat recorded in database.md, made visible rather than discovered.
        // completed_at is rewritten rather than appended to, so reopening a finished
        // task takes it out of the week it was once counted in.
        Scenario scenario = scenario();
        ZoneId zone = zoneOf(scenario);
        LocalDate today = LocalDate.now(zone);
        LocalDate twoDaysAgo = today.minusDays(2);

        TaskResponse task = taskDue(scenario, "Finished then reopened", null, null);
        done(scenario, task);
        completedAt(task.id(), twoDaysAgo.atTime(12, 0).atZone(zone).toInstant());

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(4) + "&to=" + today
                                + "&granularity=DAY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(jsonPath("$[?(@.bucketStart == '" + twoDaysAgo + "')].completed")
                        .value(1));

        taskFixtures.moveTask(scenario.workspaceId(), task.id(), "IN_PROGRESS", scenario.adminId());

        mockMvc.perform(get(reportPath(scenario, "/trends") + "?from=" + today.minusDays(4) + "&to=" + today
                                + "&granularity=DAY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(jsonPath("$[?(@.bucketStart == '" + twoDaysAgo + "')].completed")
                        .value(0));
    }

    /**
     * Writes the completion moment directly.
     *
     * <p>The one piece of SQL in this package that is not a schema assertion, for the reason at the
     * top of the class. It writes only the column the services cannot be told to write.
     */
    private void completedAt(UUID taskId, Instant moment) {
        jdbc.update("UPDATE tasks SET completed_at = ? WHERE id = ?", java.sql.Timestamp.from(moment), taskId);
    }

    /** Puts the workspace in a named timezone, which is what "today" is then measured against. */
    private void setTimezone(Scenario scenario, ZoneId zone) {
        jdbc.update("UPDATE workspaces SET timezone = ? WHERE id = ?", zone.getId(), scenario.workspaceId());
    }

    private ZoneId zoneOf(Scenario scenario) {
        String configured = jdbc.queryForObject(
                "SELECT timezone FROM workspaces WHERE id = ?", String.class, scenario.workspaceId());
        return ZoneId.of(configured == null || configured.isBlank() ? "UTC" : configured);
    }
}
