package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.support.CollaborationFixtures;
import com.company.taskmanagementplatform.teams.dto.TeamResponse;

/**
 * That a message names the work when the reader may see it, and stops naming it when they may not.
 *
 * <p>The degraded case is the one worth having. A feed that kept naming a task after its reader lost
 * access to the project would be a leak no authorization test would catch, because nothing is
 * fetched that should not be: the name was already in the row.
 */
class NotificationRenderingIT extends NotificationTestBase {

    @Autowired
    private DeadlineScanner scanner;

    @Test
    void aMessageIsComposedFromTheNameTheActorHoldsNow() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());

        eventually(() -> mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message")
                        .value("Test Person mentioned you on Build authentication."))
                .andExpect(jsonPath("$.content[0].actorName").value("Test Person"))
                .andExpect(jsonPath("$.content[0].taskKey").value(scene.projectKey() + "-1")));
    }

    @Test
    void aDeadlineMessageNamesNobodyAsItsSender() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Ship the thing", LocalDate.now(), assignee);

        // Driven directly: the schedule is off in tests.
        scanner.run();

        mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(assignee)))
                .andExpect(jsonPath("$.content[0].message").value("Ship the thing is due today."))
                .andExpect(jsonPath("$.content[0].actorName").doesNotExist())
                .andExpect(jsonPath("$.content[0].actorUserId").doesNotExist());
    }

    @Test
    void aTaskThatHasBeenRemovedIsNoLongerNamed() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1));

        taskFixtures.deleteTask(scene.workspaceId(), scene.taskId(), scene.adminId());

        // The row stays in the feed. It simply stops offering a link to something gone.
        mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].message").value("Test Person mentioned you on a task."))
                .andExpect(jsonPath("$.content[0].taskKey").doesNotExist());
    }

    @Test
    void losingSightOfAProjectStopsTheMessageNamingItsWork() throws Exception {
        // A lead reaches a project by leading its team rather than by being a member,
        // so moving the project to another team takes their access away without any
        // membership being removed. Nothing publishes an event for that, which is
        // exactly why the read path checks rather than trusting the row.
        Scene scene = scene();
        UUID lead = member(scene, "TEAM_LEAD");
        TeamResponse theirs = taskFixtures.team(scene.workspaceId(), uniqueSlug("Theirs"), lead, scene.adminId());
        moveProjectToTeam(scene, theirs.id());

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(lead), scene.adminId());
        eventually(() -> assertThat(countFor(lead, "comment.mentioned")).isEqualTo(1));

        mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(jsonPath("$.content[0].taskKey").value(scene.projectKey() + "-1"));

        UUID other = member(scene, "TEAM_LEAD");
        TeamResponse elsewhere =
                taskFixtures.team(scene.workspaceId(), uniqueSlug("Elsewhere"), other, scene.adminId());
        moveProjectToTeam(scene, elsewhere.id());

        mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].message").value("Test Person mentioned you on a task."))
                .andExpect(jsonPath("$.content[0].taskKey").doesNotExist());
    }

    @Test
    void anAdministratorSeesEveryNameBecauseTheyCanSeeEveryProject() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), colleague);
        // The admin is the task's reporter, so they hear about the comment.
        eventually(() -> assertThat(countFor(scene.adminId(), "comment.created")).isEqualTo(1));

        mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(jsonPath("$.content[0].taskKey").value(scene.projectKey() + "-1"))
                .andExpect(jsonPath("$.content[0].entityType").value("COMMENT"));
    }

    private void moveProjectToTeam(Scene scene, UUID teamId) throws Exception {
        mockMvc.perform(patch(workspacePath(scene) + "/projects/" + scene.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("teamId", teamId))))
                .andExpect(status().isOk());
    }
}
