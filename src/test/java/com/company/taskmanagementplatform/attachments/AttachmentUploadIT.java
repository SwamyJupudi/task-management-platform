package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/** Putting a file on a task, and what comes back when you do. */
class AttachmentUploadIT extends AbstractCollaborationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anUploadedFileCarriesItsUploaderSizeAndChecksum() throws Exception {
        Scene scene = scene();

        upload(scene, scene.adminId(), "architecture.pdf", CollaborationFixtures.PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("architecture.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(CollaborationFixtures.PDF.length))
                .andExpect(jsonPath("$.uploaderUserId").value(scene.adminId().toString()))
                .andExpect(jsonPath("$.uploaderName").value("Test Person"))
                .andExpect(jsonPath("$.checksumSha256").isNotEmpty())
                .andExpect(jsonPath("$.commentId").doesNotExist())
                .andExpect(jsonPath("$.taskId").value(scene.taskId().toString()));
    }

    @Test
    void theStorageKeyIsNeverReturned() throws Exception {
        // It is an internal address and the only thing between a bucket listing
        // and a file.
        Scene scene = scene();

        upload(scene, scene.adminId(), "notes.txt", CollaborationFixtures.TEXT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.storageProvider").doesNotExist());
    }

    @Test
    void theStoredKeyContainsNothingTheUploaderSupplied() {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "../../etc/passwd", CollaborationFixtures.TEXT, scene.adminId());

        String key = jdbc.queryForObject(
                "SELECT storage_key FROM attachments WHERE id = ?", String.class, file.id());

        assertThat(key).startsWith("workspace/" + scene.workspaceId() + "/task/" + scene.taskId() + "/");
        assertThat(key).doesNotContain("passwd", "..");
    }

    @Test
    void theTypeIsDetectedRatherThanTakenFromTheClient() throws Exception {
        // The part is declared as application/octet-stream by the fixture, and the
        // name says .txt. Neither is consulted: the bytes are a PNG.
        Scene scene = scene();

        upload(scene, scene.adminId(), "not-really.txt", CollaborationFixtures.PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void aTasksFilesComeBackOldestFirst() throws Exception {
        Scene scene = scene();
        collaboration.attachment(ref(scene), "one.txt", CollaborationFixtures.TEXT, scene.adminId());
        collaboration.attachment(ref(scene), "two.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(get(attachmentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].filename").value("one.txt"))
                .andExpect(jsonPath("$[1].filename").value("two.pdf"));
    }

    @Test
    void oneFilesDetailsCanBeFetchedOnItsOwn() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "design.png", CollaborationFixtures.PNG, scene.adminId());

        mockMvc.perform(get(attachmentPath(scene, file.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("design.png"));
    }

    @Test
    void anEmployeeOnTheProjectMayUpload() throws Exception {
        // The requirements give an employee "upload attachments" plainly.
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        upload(scene, employee, "evidence.png", CollaborationFixtures.PNG).andExpect(status().isCreated());
    }

    @Test
    void somebodyOffTheProjectCannotUploadOrList() throws Exception {
        Scene scene = scene();
        UUID outsider = member(scene, "EMPLOYEE");

        upload(scene, outsider, "evidence.png", CollaborationFixtures.PNG).andExpect(status().isNotFound());
        mockMvc.perform(get(attachmentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRemovedFileLeavesTheListing() throws Exception {
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "wrong.pdf", CollaborationFixtures.PDF, scene.adminId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(attachmentPath(scene, file.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(attachmentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removingAFileLeavesItsBytesForThePurge() throws Exception {
        // Soft deletion is the platform convention where restoring matters, and it
        // would be a strange restore that brought back a row pointing at nothing.
        // The purge that reclaims the storage is hardening-phase work.
        Scene scene = scene();
        AttachmentResponse file =
                collaboration.attachment(ref(scene), "wrong.pdf", CollaborationFixtures.PDF, scene.adminId());
        String key = jdbc.queryForObject(
                "SELECT storage_key FROM attachments WHERE id = ?", String.class, file.id());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(attachmentPath(scene, file.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNoContent());

        assertThat(java.nio.file.Path.of("./target/test-attachments").resolve(key)).exists();
    }

    private ResultActions upload(Scene scene, UUID actorId, String filename, byte[] content) throws Exception {
        return mockMvc.perform(multipart(attachmentsPath(scene))
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorId)));
    }
}
