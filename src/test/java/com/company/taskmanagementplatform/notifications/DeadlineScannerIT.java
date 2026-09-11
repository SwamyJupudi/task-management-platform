package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * The one trigger nobody performs.
 *
 * <p>The scan is driven directly rather than by its schedule, which is why it is switched off in the
 * test profile. A test that waited until seven in the morning would not be a test, and a background
 * job the suite did not start would race every assertion here.
 *
 * <p>Nothing in this class uses {@code eventually}. The scan writes on the calling thread, unlike the
 * event listeners, so by the time {@code run} returns the rows are there.
 */
class DeadlineScannerIT extends NotificationTestBase {

    @Autowired
    private DeadlineScanner scanner;

    @Test
    void aTaskDueInsideTheWindowNotifiesItsAssignee() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        TaskResponse task = taskDueOn(scene, "Due tomorrow", LocalDate.now().plusDays(1), assignee);

        scanner.run();

        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(1);
        assertThat(latestFor(assignee, "task.deadline_approaching").get("entity_id")).isEqualTo(task.id());
    }

    @Test
    void aTaskDueTodayIsApproaching() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due today", LocalDate.now(), assignee);

        scanner.run();

        // jsonb comes back with PostgreSQL's own spacing, so the value is what is
        // asserted rather than the exact rendering of the document.
        assertThat(latestFor(assignee, "task.deadline_approaching").get("metadata").toString())
                .contains("\"daysRemaining\": 0")
                .contains(LocalDate.now().toString());
    }

    @Test
    void aTaskDueBeyondTheWindowIsLeftAlone() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due next month", LocalDate.now().plusDays(30), assignee);

        scanner.run();

        assertThat(countFor(assignee, "task.deadline_approaching")).isZero();
    }

    @Test
    void overdueWorkIsNotApproaching() throws Exception {
        // Chasing overdue work is a dashboard's job, and the dashboards are phase eight.
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due last week", LocalDate.now().minusDays(7), assignee);

        scanner.run();

        assertThat(countFor(assignee, "task.deadline_approaching")).isZero();
    }

    @Test
    void finishedWorkIsNotChased() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        TaskResponse task = taskDueOn(scene, "Already done", LocalDate.now().plusDays(1), assignee);
        taskFixtures.moveTask(scene.workspaceId(), task.id(), "DONE", scene.adminId());

        scanner.run();

        assertThat(countFor(assignee, "task.deadline_approaching")).isZero();
    }

    @Test
    void anUnassignedTaskNotifiesNobody() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Nobody's yet", LocalDate.now().plusDays(1), null);

        scanner.run();

        assertThat(countFor(assignee, "task.deadline_approaching")).isZero();
    }

    @Test
    void aDeletedTaskIsNotChasedEither() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        TaskResponse task = taskDueOn(scene, "Removed", LocalDate.now().plusDays(1), assignee);
        taskFixtures.deleteTask(scene.workspaceId(), task.id(), scene.adminId());

        scanner.run();

        assertThat(countFor(assignee, "task.deadline_approaching")).isZero();
    }

    @Test
    void aSecondRunWritesNothing() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due tomorrow", LocalDate.now().plusDays(1), assignee);

        scanner.run();
        scanner.run();
        scanner.run();

        // The unique index on the dedupe key refuses the second and third copies,
        // which is what makes a re-run after a crash cost nothing.
        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(1);
    }

    @Test
    void aMovedDueDateNotifiesAgain() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        TaskResponse task = taskDueOn(scene, "Slipping", LocalDate.now(), assignee);

        scanner.run();
        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(1);

        mockMvc.perform(patch(workspacePath(scene) + "/tasks/" + task.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("dueDate", LocalDate.now().plusDays(2).toString()))))
                .andExpect(status().isOk());

        scanner.run();

        // A new deadline is a new thing to say, and the key changed with it.
        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(2);
    }

    @Test
    void theScanCrossesEveryWorkspaceInOnePass() throws Exception {
        Scene first = scene();
        Scene second = scene();
        UUID here = settledProjectMember(first, "EMPLOYEE");
        UUID there = settledProjectMember(second, "EMPLOYEE");
        taskDueOn(first, "Due here", LocalDate.now().plusDays(1), here);
        taskDueOn(second, "Due there", LocalDate.now().plusDays(1), there);

        scanner.run();

        assertThat(countFor(here, "task.deadline_approaching")).isEqualTo(1);
        assertThat(countFor(there, "task.deadline_approaching")).isEqualTo(1);
    }

    @Test
    void moreTasksThanOnePageAreAllNotified() throws Exception {
        // The test profile sets a batch size of two, so three tasks is two pages
        // and a remainder. Paging that stopped after the first page would leave the
        // third person untold, which is the defect this is here to catch.
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "One", LocalDate.now().plusDays(1), assignee);
        taskDueOn(scene, "Two", LocalDate.now().plusDays(1), assignee);
        taskDueOn(scene, "Three", LocalDate.now().plusDays(1), assignee);

        scanner.run();

        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(3);
    }

    @Test
    void aDeadlineRowNamesNobodyAsItsActor() throws Exception {
        // Nobody did this. Inventing a system user would put a fictional person in
        // somebody's feed.
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due tomorrow", LocalDate.now().plusDays(1), assignee);

        scanner.run();

        assertThat(latestFor(assignee, "task.deadline_approaching").get("actor_user_id")).isNull();
    }
}
