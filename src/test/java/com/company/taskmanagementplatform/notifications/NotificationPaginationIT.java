package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.support.CollaborationFixtures;

/** Ordering, page size, and the default that keeps a feed from fetching everything. */
class NotificationPaginationIT extends NotificationTestBase {

    @Test
    void theFeedIsNewestFirst() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "first " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1));

        collaboration.comment(ref(scene), "second " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(2));

        // The newest is a mention; the oldest is the row from joining the project.
        mockMvc.perform(get(notificationsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("comment.mentioned"))
                .andExpect(jsonPath("$.content[2].type").value("project.member_added"));
    }

    @Test
    void aPageIsHonouredAndReportsWhereItIs() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "first " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(1));
        collaboration.comment(ref(scene), "second " + CollaborationFixtures.mention(colleague), scene.adminId());
        eventually(() -> assertThat(countFor(colleague, "comment.mentioned")).isEqualTo(2));

        mockMvc.perform(get(notificationsPath(scene) + "?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get(notificationsPath(scene) + "?page=1&size=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void theDefaultPageSizeIsThirtyRatherThanEverything() throws Exception {
        Scene scene = scene();
        UUID colleague = settledProjectMember(scene, "EMPLOYEE");

        mockMvc.perform(get(notificationsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.size").value(30));
    }

    @Test
    void anEmptyFeedIsAPageRatherThanAnError() throws Exception {
        Scene scene = scene();
        UUID newcomer = member(scene, "EMPLOYEE");

        mockMvc.perform(get(notificationsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(newcomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }
}
