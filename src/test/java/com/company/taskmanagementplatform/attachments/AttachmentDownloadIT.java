package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * Getting the bytes back, and who is allowed to.
 *
 * <p>Downloading needs nothing but being able to see the task. There is no attachment read
 * permission, for the same reason there is no comment read permission and no {@code task:read_any}:
 * a file is visible exactly when its task is.
 */
class AttachmentDownloadIT extends AbstractCollaborationIT {

    @Test
    void theBytesComeBackUnchanged() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "notes.txt", CollaborationFixtures.TEXT, scene.adminId());

        MvcResult result = mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(CollaborationFixtures.TEXT);
    }

    @Test
    void aDownloadIsAlwaysAnAttachmentAndNeverRenderedInline() throws Exception {
        // The second lock on the door the content-type allowlist is the first lock
        // on: even a file the browser could render must not be rendered.
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "design.png", CollaborationFixtures.PNG, scene.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentTypeCompatibleWith("image/png"));
    }

    @Test
    void theFilenameSurvivesInTheHeader() throws Exception {
        Scene scene = scene();
        AttachmentResponse file = collaboration.attachment(
                ref(scene), "design notes über.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("filename")));
    }

    @Test
    void anEmployeeOnTheProjectMayDownload() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "spec.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk());
    }

    @Test
    void somebodyOffTheProjectCannotDownloadOrEvenSeeItExists() throws Exception {
        Scene scene = scene();
        UUID outsider = member(scene, "EMPLOYEE");
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "confidential.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(attachmentPath(scene, file.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFileFromAnotherWorkspaceIsNotFound() throws Exception {
        Scene scene = scene();
        Scene other = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(other), "elsewhere.pdf", CollaborationFixtures.PDF, other.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRemovedFileCannotBeDownloaded() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "gone.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(attachmentPath(scene, file.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "spec.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()) + "/content"))
                .andExpect(status().isUnauthorized());
    }
}
