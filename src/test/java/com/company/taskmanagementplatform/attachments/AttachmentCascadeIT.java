package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.projects.ProjectService;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/** What happens to a file when the comment, task or project holding it goes away. */
class AttachmentCascadeIT extends AbstractCollaborationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectService projects;

    @Autowired
    private MembershipService memberships;

    @Autowired
    private UserAccountService users;

    @Test
    void removingACommentTakesTheFilesThatBelongedToIt() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "evidence.png", CollaborationFixtures.PNG, scene.adminId());
        CommentResponse comment =
                collaboration.comment(ref(scene), "see attached", List.of(file.id()), scene.adminId());

        mockMvc.perform(MockMvcRequestBuilders.delete(commentPath(scene, comment.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        assertThat(isLive(file.id())).isFalse();
    }

    @Test
    void removingACommentLeavesTheTasksOwnFilesAlone() throws Exception {
        Scene scene = scene();
        AttachmentResponse onTheTask =
                collaboration.attachment(ref(scene), "loose.pdf", CollaborationFixtures.PDF, scene.adminId());
        AttachmentResponse onTheComment =
                collaboration.attachment(ref(scene), "evidence.png", CollaborationFixtures.PNG, scene.adminId());
        CommentResponse comment =
                collaboration.comment(ref(scene), "see attached", List.of(onTheComment.id()), scene.adminId());

        mockMvc.perform(MockMvcRequestBuilders.delete(commentPath(scene, comment.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        assertThat(isLive(onTheComment.id())).isFalse();
        assertThat(isLive(onTheTask.id())).isTrue();
    }

    @Test
    void removingATaskTakesItsFiles() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "spec.pdf", CollaborationFixtures.PDF, scene.adminId());

        taskFixtures.deleteTask(scene.workspaceId(), scene.taskId(), scene.adminId());

        assertThat(isLive(file.id())).isFalse();
    }

    @Test
    void removingAProjectTakesEveryFileInIt() {
        Scene scene = scene();
        TaskResponse second =
                taskFixtures.task(scene.workspaceId(), scene.projectId(), "Another task", scene.adminId());

        AttachmentResponse first =
                collaboration.attachment(ref(scene), "one.pdf", CollaborationFixtures.PDF, scene.adminId());
        AttachmentResponse other = collaboration.attachment(
                taskFixtures.ref(second), "two.pdf", CollaborationFixtures.PDF, scene.adminId());

        projects.delete(scene.workspaceId(), scene.projectId(), scene.adminId());

        assertThat(isLive(first.id())).isFalse();
        assertThat(isLive(other.id())).isFalse();
    }

    @Test
    void aFileOutlivesTheUploaderLeavingTheWorkspace() {
        // Keyed to users, not to a membership row, so nothing has to be stood down
        // and the file stays where it is.
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "theirs.pdf", CollaborationFixtures.PDF, employee);

        memberships.removeMember(scene.workspaceId(), employee);

        assertThat(isLive(file.id())).isTrue();
        assertThat(uploaderOf(file.id())).isEqualTo(employee);
    }

    @Test
    void aFileOutlivesTheUploadersAccountBeingRemoved() {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "theirs.pdf", CollaborationFixtures.PDF, employee);

        users.softDelete(employee);

        assertThat(isLive(file.id())).isTrue();
    }

    private boolean isLive(UUID attachmentId) {
        Integer live = jdbc.queryForObject(
                "SELECT count(*) FROM attachments WHERE id = ? AND deleted_at IS NULL", Integer.class, attachmentId);
        return live != null && live == 1;
    }

    private UUID uploaderOf(UUID attachmentId) {
        return jdbc.queryForObject(
                "SELECT uploader_user_id FROM attachments WHERE id = ?", UUID.class, attachmentId);
    }
}
