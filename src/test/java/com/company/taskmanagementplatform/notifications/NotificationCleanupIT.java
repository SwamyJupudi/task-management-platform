package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.support.CollaborationFixtures;
import com.company.taskmanagementplatform.users.UserAccount;

/**
 * That a notification does not outlive the access it implies.
 *
 * <p>The opposite of phase six's rule, deliberately. A comment must outlive its author leaving the
 * workspace; a message saying "come and look at this" must not outlive the recipient's ability to
 * look at it.
 */
class NotificationCleanupIT extends NotificationTestBase {

    @Test
    void leavingAWorkspaceTakesItsNotificationsAway() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1));

        mockMvc.perform(delete(workspacePath(scene) + "/members/" + colleague)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        assertThat(rowsFor(colleague)).isEmpty();
    }

    @Test
    void leavingOneProjectTakesOnlyThatProjectsNotifications() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1));

        mockMvc.perform(delete(workspacePath(scene) + "/projects/" + scene.projectId() + "/members/" + colleague)
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        // They are still in the workspace; what has gone is what pointed at the project.
        assertThat(rowsFor(colleague)).isEmpty();
        assertThat(countFor(colleague, "comment.mentioned")).isZero();
    }

    @Test
    void aNotificationInAnotherProjectSurvivesLeavingThisOne() throws Exception {
        Scene first = scene();
        UUID colleague = settledProjectMember(first, "EMPLOYEE");

        // A second project in the same workspace, and a mention on each.
        var secondProject = taskFixtures.project(first.workspaceId(), uniqueKey(), first.adminId());
        taskFixtures.addProjectMember(first.workspaceId(), secondProject.id(), colleague, first.adminId());
        var secondTask =
                taskFixtures.task(first.workspaceId(), secondProject.id(), "Elsewhere", first.adminId());

        collaboration.comment(ref(first), "here " + CollaborationFixtures.mention(colleague), first.adminId());
        collaboration.comment(
                taskFixtures.ref(secondTask), "there " + CollaborationFixtures.mention(colleague), first.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(2));

        mockMvc.perform(delete(workspacePath(first) + "/projects/" + first.projectId() + "/members/" + colleague)
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.adminId())))
                .andExpect(status().isNoContent());

        assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1);
    }

    @Test
    void removingAnAccountTakesItsNotificationsWithIt() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1));

        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));

        mockMvc.perform(delete("/api/v1/users/" + colleague)
                        .header(HttpHeaders.AUTHORIZATION, bearer(platform.id())))
                .andExpect(status().isNoContent());

        assertThat(rowsFor(colleague)).isEmpty();
    }

    @Test
    void aSoftDeletedTaskLeavesTheNotificationAlone() throws Exception {
        // Deliberately not a cleanup case. The row stays and the read path renders it
        // without a link, rather than this module listening to half the platform.
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1));

        taskFixtures.deleteTask(scene.workspaceId(), scene.taskId(), scene.adminId());

        assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1);
    }
}
