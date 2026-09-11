package com.company.taskmanagementplatform.notifications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * That a notification has exactly one audience.
 *
 * <p>This is the test that stands in for the permission code this module deliberately does not have.
 * Every other module proves its rule by showing that a missing grant answers 403; here there is no
 * grant to miss, so the rule is proved by showing that the widest possible caller still cannot read
 * somebody else's feed.
 */
class NotificationIsolationIT extends NotificationTestBase {

    @Test
    void oneFeedShowsOnlyItsOwnersNotifications() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        UUID other = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());

        eventually(() -> mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.totalElements").value(1)));

        mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anAdministratorCannotReadSomebodyElsesNotification() throws Exception {
        // The administrative question, who was told what, is the audit trail's.
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> org.assertj.core.api.Assertions.assertThat(countFor(colleague, "comment.mentioned"))
                .isEqualTo(1));

        UUID notificationId = (UUID) latestFor(colleague, "comment.mentioned").get("id");

        mockMvc.perform(patch(notificationsPath(scene) + "/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void markingEverythingReadClearsOnlyYourOwn() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> org.assertj.core.api.Assertions.assertThat(countFor(colleague, "comment.mentioned"))
                .isEqualTo(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(notificationsPath(scene) + "/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk());

        mockMvc.perform(get(notificationsPath(scene) + "/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.unread").value(1));
    }
}
