package com.company.taskmanagementplatform.comments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;

/**
 * Writing on a task, editing what you wrote, and removing it.
 *
 * <p>The requirements ask for add, edit and delete "where permitted", together with timestamps and
 * user info. Who is permitted is {@code CommentAuthorizationIT}; this is about what the operations
 * actually do.
 */
class CommentManagementIT extends AbstractCollaborationIT {

    @Test
    void aCommentCarriesItsAuthorAndTheMomentItWasWritten() throws Exception {
        Scene scene = scene();

        write(scene, scene.adminId(), "the tests still fail on windows")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("the tests still fail on windows"))
                .andExpect(jsonPath("$.authorUserId").value(scene.adminId().toString()))
                .andExpect(jsonPath("$.authorName").value("Test Person"))
                .andExpect(jsonPath("$.authorEmail").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.edited").value(false))
                .andExpect(jsonPath("$.editedAt").doesNotExist())
                .andExpect(jsonPath("$.mentions.length()").value(0));
    }

    @Test
    void aThreadComesBackOldestFirst() throws Exception {
        Scene scene = scene();
        collaboration.comment(ref(scene), "first", scene.adminId());
        collaboration.comment(ref(scene), "second", scene.adminId());
        collaboration.comment(ref(scene), "third", scene.adminId());

        mockMvc.perform(get(commentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].body").value("first"))
                .andExpect(jsonPath("$.content[2].body").value("third"));
    }

    @Test
    void aLongThreadIsPaged() throws Exception {
        Scene scene = scene();
        for (int i = 0; i < 5; i++) {
            collaboration.comment(ref(scene), "remark " + i, scene.adminId());
        }

        mockMvc.perform(get(commentsPath(scene) + "?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void editingRecordsWhenTheWordsChanged() throws Exception {
        Scene scene = scene();
        CommentResponse comment = collaboration.comment(ref(scene), "looks fine", scene.adminId());

        edit(scene, comment.id(), scene.adminId(), "on reflection it does not")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("on reflection it does not"))
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.editedAt").exists());
    }

    @Test
    void aRemovedCommentLeavesTheThread() throws Exception {
        Scene scene = scene();
        CommentResponse comment = collaboration.comment(ref(scene), "never mind", scene.adminId());

        mockMvc.perform(delete(commentPath(scene, comment.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(commentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aRemovedCommentCannotBeEditedOrRemovedAgain() throws Exception {
        Scene scene = scene();
        CommentResponse comment = collaboration.comment(ref(scene), "never mind", scene.adminId());

        mockMvc.perform(delete(commentPath(scene, comment.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        edit(scene, comment.id(), scene.adminId(), "back again").andExpect(status().isNotFound());
        mockMvc.perform(delete(commentPath(scene, comment.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmptyCommentIsRefused() throws Exception {
        Scene scene = scene();

        write(scene, scene.adminId(), "   ").andExpect(status().isBadRequest());
    }

    @Test
    void aCommentPastTheLimitIsRefused() throws Exception {
        Scene scene = scene();

        write(scene, scene.adminId(), "x".repeat(5001)).andExpect(status().isBadRequest());
    }

    @Test
    void markupIsStoredAsTheTextItIsRatherThanBeingRewritten() throws Exception {
        // Escaping belongs to whatever renders it. Rewriting somebody's words here
        // would be worse than storing them, and the body is never served as HTML.
        Scene scene = scene();
        String body = "try <script>alert(1)</script> in the payload";

        write(scene, scene.adminId(), body).andExpect(status().isCreated()).andExpect(jsonPath("$.body").value(body));
    }

    @Test
    void aCommentOnATaskFromAnotherWorkspaceIsNotFound() throws Exception {
        Scene scene = scene();
        Scene other = scene();

        mockMvc.perform(post(workspacePath(scene) + "/tasks/" + other.taskId() + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "hello"))))
                .andExpect(status().isNotFound());
    }

    private ResultActions write(Scene scene, UUID actorId, String body) throws Exception {
        return mockMvc.perform(post(commentsPath(scene))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("body", body))));
    }

    private ResultActions edit(Scene scene, UUID commentId, UUID actorId, String body) throws Exception {
        return mockMvc.perform(patch(commentPath(scene, commentId))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("body", body))));
    }
}
