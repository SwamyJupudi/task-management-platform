package com.company.taskmanagementplatform.attachments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * What the platform refuses to store, end to end.
 *
 * <p>{@code ContentTypesTest} covers the sniffer on its own. This is the same rules reached through
 * the API, which is where they actually have to hold: the multipart part carries a declared type and
 * a filename, and neither is allowed to change the outcome.
 */
class AttachmentValidationIT extends AbstractCollaborationIT {

    @Test
    void anExecutableRenamedAsAnImageIsRefused() throws Exception {
        // The whole reason the type is detected rather than believed.
        Scene scene = scene();
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        upload(scene, "holiday-photo.png", "image/png", exe)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void anSvgIsRefusedBecauseBrowsersExecuteIt() throws Exception {
        Scene scene = scene();
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        upload(scene, "logo.svg", "image/svg+xml", svg).andExpect(status().isBadRequest());
    }

    @Test
    void htmlIsRefusedByTheSameRule() throws Exception {
        Scene scene = scene();

        upload(scene, "page.html", "text/html", "<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anEmptyFileIsRefused() throws Exception {
        Scene scene = scene();

        upload(scene, "nothing.txt", "text/plain", new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    void aFileOverTheApplicationLimitIsRefusedWithThirteen() throws Exception {
        // The test profile sets the application limit to 1MB and the container
        // limit to 2MB, so this exercises the application's own check rather than
        // the container's.
        Scene scene = scene();
        byte[] big = new byte[1024 * 1024 + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        big[4] = '-';

        upload(scene, "huge.pdf", "application/pdf", big).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void aFileFarOverTheLimitIsRefusedTheSameWay() throws Exception {
        // MockMvc assembles the multipart itself rather than running the servlet
        // container's parser, so both of these reach the application's own check
        // and neither exercises spring.servlet.multipart. That limit is
        // configuration, and the handler that turns it into 413 was written and
        // tested in phase one; what matters here is that one status comes back
        // whatever the size.
        Scene scene = scene();
        byte[] enormous = new byte[3 * 1024 * 1024];

        upload(scene, "enormous.pdf", "application/pdf", enormous).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void anAcceptedFileIsStoredUnderASanitisedName() throws Exception {
        Scene scene = scene();

        upload(scene, "../../../etc/report.pdf", "application/pdf", CollaborationFixtures.PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("report.pdf"));
    }

    @Test
    void aNameThatCouldForgeAHeaderIsCleaned() throws Exception {
        Scene scene = scene();
        String hostile = "report" + (char) 13 + (char) 10 + "X-Evil: yes.pdf";

        upload(scene, hostile, "application/pdf", CollaborationFixtures.PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("reportX-Evil: yes.pdf"));
    }

    @Test
    void everyAcceptedKindIsAccepted() throws Exception {
        Scene scene = scene();

        upload(scene, "a.png", "image/png", CollaborationFixtures.PNG).andExpect(status().isCreated());
        upload(scene, "b.pdf", "application/pdf", CollaborationFixtures.PDF).andExpect(status().isCreated());
        upload(scene, "c.txt", "text/plain", CollaborationFixtures.TEXT).andExpect(status().isCreated());
        upload(scene, "d.csv", "text/csv", "name,role\nada,engineer\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("text/plain"));
    }

    @Test
    void somebodyWithoutTheUploadGrantIsRefused() throws Exception {
        // Nobody holds attachment:create without also being able to see tasks, so
        // this is checked from the other end: an outsider gets 404, and the
        // permission itself is asserted by WorkspaceRoleGrantsIT.
        Scene scene = scene();
        UUID outsider = member(scene, "EMPLOYEE");

        mockMvc.perform(multipart(attachmentsPath(scene))
                        .file(new MockMultipartFile("file", "x.png", "image/png", CollaborationFixtures.PNG))
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    private ResultActions upload(Scene scene, String filename, String declaredType, byte[] content)
            throws Exception {
        return mockMvc.perform(multipart(attachmentsPath(scene))
                .file(new MockMultipartFile("file", filename, declaredType, content))
                .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())));
    }
}
