package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * The second half of the two-step attachment flow: upload to the task, then claim the file when
 * writing the comment.
 *
 * <p>Four rules are checked here and each of them is a way the operation could otherwise be used to
 * reach something that is not yours.
 */
class AttachmentAdoptionIT extends AbstractCollaborationIT {

    @Test
    void aCommentClaimsAFileUploadedAMomentEarlier() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "evidence.png", CollaborationFixtures.PNG, scene.adminId());

        mockMvc.perform(post(commentsPath(scene))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("body", "see attached", "attachmentIds", List.of(file.id())))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(attachmentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(jsonPath("$[0].commentId").exists());
    }

    @Test
    void aFileWithNoCommentHangsOffTheTaskItself() throws Exception {
        Scene scene = scene();
        collaboration.attachment(ref(scene), "loose.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(get(attachmentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(jsonPath("$[0].commentId").doesNotExist());
    }

    @Test
    void youCannotClaimSomebodyElsesUpload() {
        // Otherwise a colleague's file could be pulled into your own words.
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        AttachmentResponse theirs =
                collaboration.attachment(ref(scene), "theirs.png", CollaborationFixtures.PNG, employee);

        assertThatThrownBy(() ->
                        collaboration.comment(ref(scene), "mine now", List.of(theirs.id()), scene.adminId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("you uploaded yourself");
    }

    @Test
    void youCannotClaimAFileFromAnotherTask() {
        Scene scene = scene();
        TaskResponse elsewhere =
                taskFixtures.task(scene.workspaceId(), scene.projectId(), "Another task", scene.adminId());
        AttachmentResponse file = collaboration.attachment(
                taskFixtures.ref(elsewhere), "over-there.png", CollaborationFixtures.PNG, scene.adminId());

        assertThatThrownBy(() ->
                        collaboration.comment(ref(scene), "see attached", List.of(file.id()), scene.adminId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No such file on this task");
    }

    @Test
    void aFileCannotBeMovedOutFromUnderAnExistingComment() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "evidence.png", CollaborationFixtures.PNG, scene.adminId());
        collaboration.comment(ref(scene), "first", List.of(file.id()), scene.adminId());

        assertThatThrownBy(() ->
                        collaboration.comment(ref(scene), "second", List.of(file.id()), scene.adminId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already belongs");
    }

    @Test
    void claimingSomethingThatDoesNotExistIsRefused() {
        Scene scene = scene();

        assertThatThrownBy(() ->
                        collaboration.comment(ref(scene), "see attached", List.of(UUID.randomUUID()), scene.adminId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aRefusedClaimLeavesNoComment() throws Exception {
        Scene scene = scene();

        mockMvc.perform(post(commentsPath(scene))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("body", "see attached", "attachmentIds", List.of(UUID.randomUUID())))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(commentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aCommentsFilesAreListedWithTheTasksFiles() throws Exception {
        // A comment does not carry its attachments in its own body; a client groups
        // the task's files by the comment each one names.
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "evidence.png", CollaborationFixtures.PNG, scene.adminId());
        CommentResponse comment =
                collaboration.comment(ref(scene), "see attached", List.of(file.id()), scene.adminId());

        mockMvc.perform(get(attachmentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commentId").value(comment.id().toString()));

        assertThat(comment.id()).isNotNull();
    }
}
