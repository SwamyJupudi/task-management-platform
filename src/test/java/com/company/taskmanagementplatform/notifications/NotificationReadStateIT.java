package com.company.taskmanagementplatform.notifications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.support.CollaborationFixtures;

/** The badge, the history, and the two ways to clear them. */
class NotificationReadStateIT extends NotificationTestBase {

    @Test
    void aNewNotificationIsUnread() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        mention(scene, colleague);

        eventually(() -> mockMvc.perform(get(notificationsPath(scene) + "/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1)));
    }

    @Test
    void markingOneReadClearsItFromTheBadge() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        mention(scene, colleague);

        UUID notificationId = firstNotificationId(scene, colleague);

        mockMvc.perform(patch(notificationsPath(scene) + "/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(notificationsPath(scene) + "/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void markingSomethingReadTwiceSucceedsAndChangesNothing() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        mention(scene, colleague);

        UUID notificationId = firstNotificationId(scene, colleague);
        String path = notificationsPath(scene) + "/" + notificationId + "/read";

        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isNoContent());
        Object first = latestFor(colleague, "comment.mentioned").get("read_at");

        mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isNoContent());

        // The moment somebody first read it is not rewritten by reading it again.
        org.assertj.core.api.Assertions.assertThat(latestFor(colleague, "comment.mentioned").get("read_at"))
                .isEqualTo(first);
    }

    @Test
    void markingEverythingReadReportsHowManyItMoved() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        mention(scene, colleague);
        mention(scene, colleague);

        eventually(() -> mockMvc.perform(get(notificationsPath(scene) + "/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.unread").value(2)));

        mockMvc.perform(post(notificationsPath(scene) + "/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(2));

        mockMvc.perform(post(notificationsPath(scene) + "/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.marked").value(0));
    }

    @Test
    void theUnreadFilterNarrowsTheHistoryWithoutShorteningIt() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        mention(scene, colleague);
        mention(scene, colleague);

        // Three rows: the joining notification the setup cleared, and two mentions.
        eventually(() -> mockMvc.perform(get(notificationsPath(scene))
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.totalElements").value(3)));

        UUID notificationId = firstNotificationId(scene, colleague);
        mockMvc.perform(patch(notificationsPath(scene) + "/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isNoContent());

        // History keeps both; the unread listing shows one. The requirements ask for
        // read/unread status and notification history, which are different things.
        mockMvc.perform(get(notificationsPath(scene))
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(notificationsPath(scene) + "?unread=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void aNotificationThatIsNotYoursCannotBeMarkedRead() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");
        UUID stranger = settledProjectMember(scene, "EMPLOYEE");
        mention(scene, colleague);

        UUID notificationId = firstNotificationId(scene, colleague);

        mockMvc.perform(patch(notificationsPath(scene) + "/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    private void mention(Scene scene, UUID colleague) {
        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());
    }

    private UUID firstNotificationId(Scene scene, UUID recipient) {
        eventually(() -> mockMvc.perform(get(notificationsPath(scene))
                        .header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
                .andExpect(jsonPath("$.content[0].id").exists()));

        return (UUID) latestFor(recipient, "comment.mentioned").get("id");
    }
}
