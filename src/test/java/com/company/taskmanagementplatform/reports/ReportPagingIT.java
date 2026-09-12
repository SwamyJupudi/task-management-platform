package com.company.taskmanagementplatform.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Paging and sorting on the two report listings.
 *
 * <p>The page cap is twenty-five in the test profile rather than a hundred, and the window maximum is
 * forty days rather than a year. Both are lowered so the tests that prove the bounds exist can reach
 * them: a bound that needs a large fixture to reach is a bound nobody writes a test for. The cap
 * stays above the endpoints' own default page size of twenty, or a request naming no size would be
 * refused by the guard meant for the ones naming a large one.
 */
class ReportPagingIT extends ReportApiTestBase {

    @Test
    void pagesTheOverdueListing() throws Exception {
        Scenario scenario = scenario();
        for (int day = 1; day <= 5; day++) {
            taskDue(scenario, "Late " + day, null, LocalDate.now().minusDays(day));
        }

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void putsTheLongestOverdueFirstByDefault() throws Exception {
        // The row somebody actually has to do something about.
        Scenario scenario = scenario();
        taskDue(scenario, "Two days late", null, LocalDate.now().minusDays(2));
        taskDue(scenario, "Ten days late", null, LocalDate.now().minusDays(10));

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Ten days late"));
    }

    @Test
    void sortsTheOverdueListingByEveryAllowlistedField() throws Exception {
        Scenario scenario = scenario();
        taskDue(scenario, "Alpha", null, LocalDate.now().minusDays(1));
        taskDue(scenario, "Beta", null, LocalDate.now().minusDays(2));

        for (String field : new String[] {"dueDate", "priority", "projectId", "title"}) {
            mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?sort=" + field + ",desc")
                            .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?sort=title,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Alpha"));
    }

    @Test
    void refusesASortFieldOutsideTheAllowlistAndNamesIt() throws Exception {
        // Passing the request through would let a query parameter probe the entity
        // and order a large table by a column with no index behind it.
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?sort=description,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("description")));
    }

    @Test
    void refusesAPageLargerThanTheCap() throws Exception {
        // Refused rather than silently clamped: a clamped page makes a client's own
        // paging arithmetic wrong without telling them.
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?size=26")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(reportPath(scenario, "/workload") + "?size=26")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(reportPath(scenario, "/projects") + "?size=26")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsAPageExactlyAtTheCap() throws Exception {
        Scenario scenario = scenario();

        mockMvc.perform(get(reportPath(scenario, "/tasks/overdue") + "?size=25")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk());
    }

    @Test
    void pagesAndSortsTheWorkloadListing() throws Exception {
        Scenario scenario = scenario();
        UUID busy = projectMember(scenario, "EMPLOYEE");
        UUID quiet = projectMember(scenario, "EMPLOYEE");

        taskDue(scenario, "One", busy, null);
        taskDue(scenario, "Two", busy, null);
        taskDue(scenario, "Three", quiet, null);

        mockMvc.perform(get(reportPath(scenario, "/workload"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].userId").value(busy.toString()));

        mockMvc.perform(get(reportPath(scenario, "/workload") + "?sort=open,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(quiet.toString()));

        mockMvc.perform(get(reportPath(scenario, "/workload") + "?page=1&size=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void sortsTheWorkloadListingByEveryAllowlistedFieldAndRefusesAnythingElse() throws Exception {
        Scenario scenario = scenario();
        UUID employee = projectMember(scenario, "EMPLOYEE");
        taskDue(scenario, "Work", employee, LocalDate.now().minusDays(1));

        for (String field : new String[] {"open", "overdue", "completedInPeriod", "fullName"}) {
            mockMvc.perform(get(reportPath(scenario, "/workload") + "?sort=" + field + ",desc")
                            .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        mockMvc.perform(get(reportPath(scenario, "/workload") + "?sort=email,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortsTheProjectReportByProgressByDefaultAndRefusesAnythingOutsideItsList() throws Exception {
        Scenario scenario = scenario();
        done(scenario, taskDue(scenario, "Finished", null, null));
        var second = taskFixtures.project(scenario.workspaceId(), uniqueKey(), scenario.adminId());
        taskDue(scenario, second.id(), "Unfinished", null, null);

        mockMvc.perform(get(reportPath(scenario, "/projects"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].progress").value(100));

        mockMvc.perform(get(reportPath(scenario, "/projects") + "?sort=dueDate,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagesStablySoNobodyAppearsTwiceOrNotAtAll() throws Exception {
        // The workload rows are ordered in this module rather than in SQL, so the
        // total order has to be made explicit. Without a tie break, two people with
        // the same number of open tasks could land on both pages, or on neither.
        Scenario scenario = scenario();
        UUID first = projectMember(scenario, "EMPLOYEE");
        UUID second = projectMember(scenario, "EMPLOYEE");
        taskDue(scenario, "One each", first, null);
        taskDue(scenario, "And one", second, null);

        String pageOne = mockMvc.perform(get(reportPath(scenario, "/workload") + "?page=0&size=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String pageTwo = mockMvc.perform(get(reportPath(scenario, "/workload") + "?page=1&size=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scenario.adminId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstUser = json.readTree(pageOne).get("content").get(0).get("userId").asText();
        String secondUser = json.readTree(pageTwo).get("content").get(0).get("userId").asText();

        org.assertj.core.api.Assertions.assertThat(firstUser).isNotEqualTo(secondUser);
        org.assertj.core.api.Assertions.assertThat(java.util.Set.of(firstUser, secondUser))
                .containsExactlyInAnyOrder(first.toString(), second.toString());
    }
}
