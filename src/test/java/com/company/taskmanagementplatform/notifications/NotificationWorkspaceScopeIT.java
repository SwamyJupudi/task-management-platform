package com.company.taskmanagementplatform.notifications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * That the workspace in the path is enforced, even though no permission code is.
 *
 * <p>A workspace the caller has nothing to do with answers 404 rather than 403, exactly as it does
 * everywhere else in the platform: a forbidden response would confirm that the identifier names
 * something real.
 */
class NotificationWorkspaceScopeIT extends NotificationTestBase {

    @Test
    void aWorkspaceTheCallerIsNotInIsMissingRatherThanForbidden() throws Exception {
        Scene mine = scene();
        Scene theirs = scene();

        mockMvc.perform(get(notificationsPath(theirs))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void theBadgeIsRefusedTheSameWay() throws Exception {
        Scene mine = scene();
        Scene theirs = scene();

        mockMvc.perform(get(notificationsPath(theirs) + "/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWorkspaceThatDoesNotExistAnswersTheSameAsOneYouCannotSee() throws Exception {
        Scene scene = scene();

        mockMvc.perform(get(workspacePath(scene).replace(scene.workspaceId().toString(), UUID.randomUUID().toString())
                                + "/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFeedIsNarrowedToOneWorkspaceAtATime() throws Exception {
        // The same person can be in two workspaces; each feed shows one of them.
        Scene first = scene();
        Scene second = scene();

        UUID colleague = settledProjectMember(first, "EMPLOYEE");
        fixtures.addMember(second.workspaceId(), colleague, "EMPLOYEE");

        collaboration.comment(ref(first), "over to " + CollaborationFixtures.mention(colleague), first.adminId());

        eventually(() -> mockMvc.perform(get(notificationsPath(first))
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(jsonPath("$.totalElements").value(2)));

        mockMvc.perform(get(notificationsPath(second))
                        .header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anUnauthenticatedCallerIsRefused() throws Exception {
        Scene scene = scene();

        mockMvc.perform(get(notificationsPath(scene))).andExpect(status().isUnauthorized());
    }
}
