package com.company.taskmanagementplatform.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * That the things people do end up in the audit trail.
 *
 * <p>This is the test that closes the loop opened in phase three. Tasks, projects and subtasks have
 * published events since they were built and nothing listened; the rows asserted here are written by
 * listeners in this module, from those same events, without one line changing in the modules that
 * publish them.
 *
 * <p>The writes happen after the publishing transaction commits, so a fixture that runs inside its
 * own transaction would not see them. Every action below therefore goes through the API or through a
 * fixture whose transaction has already ended by the time the assertion runs.
 */
class ActivityRecordingIT extends AbstractCollaborationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void creatingATaskIsRecorded() {
        Scene scene = scene();

        // The scene itself creates a project and a task, both through the services.
        eventually(() -> assertThat(actions(scene.workspaceId())).contains("project.created", "task.created"));
    }

    @Test
    void aStatusChangeRecordsBothStatuses() {
        Scene scene = scene();

        taskFixtures.moveTask(scene.workspaceId(), scene.taskId(), "IN_PROGRESS", scene.adminId());

        eventually(() -> {
            Map<String, Object> row = latest(scene.workspaceId(), "task.status_changed");
            assertThat(row.get("entity_id")).isEqualTo(scene.taskId());
            assertThat(String.valueOf(row.get("metadata"))).contains("TODO").contains("IN_PROGRESS");
        });
    }

    @Test
    void anAssignmentRecordsBothPeople() {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        taskFixtures.task(scene.workspaceId(), scene.projectId(), "Assigned on creation", employee, scene.adminId());

        eventually(() -> assertThat(String.valueOf(latest(scene.workspaceId(), "task.created").get("metadata")))
                .contains(employee.toString()));
    }

    @Test
    void aCommentIsRecordedAgainstItsOwnIdentifier() {
        Scene scene = scene();

        CommentResponse comment = collaboration.comment(ref(scene), "worth saying", scene.adminId());

        eventually(() -> {
            Map<String, Object> row = latest(scene.workspaceId(), "comment.created");
            assertThat(row.get("entity_id")).isEqualTo(comment.id());
            assertThat(row.get("entity_type")).isEqualTo("COMMENT");
            assertThat(String.valueOf(row.get("metadata"))).contains(scene.taskId().toString());
        });
    }

    @Test
    void aMentionIsCarriedInTheCommentsOwnRow() {
        // Rather than as a row of its own: the mention is part of the comment, and
        // two rows for one action would make a history read like a stutter.
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());

        eventually(() -> assertThat(String.valueOf(latest(scene.workspaceId(), "comment.created").get("metadata")))
                .contains(colleague.toString()));
    }

    @Test
    void anUploadRecordsTheFilename() {
        Scene scene = scene();

        AttachmentResponse file =
                collaboration.attachment(ref(scene), "architecture.pdf", CollaborationFixtures.PDF, scene.adminId());

        eventually(() -> {
            Map<String, Object> row = latest(scene.workspaceId(), "attachment.uploaded");
            assertThat(row.get("entity_id")).isEqualTo(file.id());
            assertThat(String.valueOf(row.get("metadata"))).contains("architecture.pdf");
        });
    }

    @Test
    void everyRowNamesTheActorAndTheProject() {
        Scene scene = scene();
        collaboration.comment(ref(scene), "worth saying", scene.adminId());

        eventually(() -> {
            Map<String, Object> row = latest(scene.workspaceId(), "comment.created");
            assertThat(row.get("actor_user_id")).isEqualTo(scene.adminId());
            assertThat(row.get("project_id")).isEqualTo(scene.projectId());
        });
    }

    @Test
    void aRowTakenThroughTheApiCarriesTheCorrelationId() throws Exception {
        // The audit row and the log lines behind it can then be read together.
        Scene scene = scene();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(commentsPath(scene))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .header("X-Request-Id", "audit-trace-1")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "through the api"))))
                .andExpect(status().isCreated());

        eventually(() -> assertThat(latest(scene.workspaceId(), "comment.created").get("request_id"))
                .isEqualTo("audit-trace-1"));
    }

    @Test
    void derivedProgressIsNotRecordedAsSomethingSomebodyDid() {
        // An audit trail is a record of what people did. Progress is a consequence.
        Scene scene = scene();
        taskFixtures.moveTask(scene.workspaceId(), scene.taskId(), "IN_PROGRESS", scene.adminId());
        taskFixtures.moveTask(scene.workspaceId(), scene.taskId(), "DONE", scene.adminId());

        // Waits for the trail to catch up first, so this asserts against a written
        // history rather than against one that has not arrived yet.
        eventually(() -> assertThat(actions(scene.workspaceId())).contains("task.status_changed"));
        assertThat(actions(scene.workspaceId())).doesNotContain("project.progress_changed");
    }

    @Test
    void aTasksHistoryIncludesWhatHappenedToItsCommentsAndFiles() throws Exception {
        Scene scene = scene();
        collaboration.comment(ref(scene), "a remark", scene.adminId());
        collaboration.attachment(ref(scene), "spec.pdf", CollaborationFixtures.PDF, scene.adminId());
        taskFixtures.moveTask(scene.workspaceId(), scene.taskId(), "IN_PROGRESS", scene.adminId());

        eventually(() -> mockMvc.perform(get(taskPath(scene) + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].action").value("task.status_changed")));
    }

    @Test
    void anEntryReadsAsASentenceComposedFromTheNameHeldNow() throws Exception {
        Scene scene = scene();
        taskFixtures.moveTask(scene.workspaceId(), scene.taskId(), "IN_PROGRESS", scene.adminId());

        eventually(() -> mockMvc.perform(get(taskPath(scene) + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].summary")
                        .value("Test Person changed the status from todo to in progress."))
                .andExpect(jsonPath("$.content[0].actorName").value("Test Person"))
                .andExpect(jsonPath("$.content[0].metadata.from").value("TODO"))
                .andExpect(jsonPath("$.content[0].metadata.to").value("IN_PROGRESS")));
    }

    @Test
    void aProjectsHistoryIsEverythingInsideIt() throws Exception {
        Scene scene = scene();
        collaboration.comment(ref(scene), "a remark", scene.adminId());

        eventually(() -> mockMvc.perform(get(workspacePath(scene) + "/projects/" + scene.projectId() + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)));
    }

    private List<String> actions(UUID workspaceId) {
        return jdbc.queryForList(
                "SELECT action FROM activity_logs WHERE workspace_id = ?", String.class, workspaceId);
    }

    private Map<String, Object> latest(UUID workspaceId, String action) {
        return jdbc.queryForMap(
                """
                SELECT * FROM activity_logs
                WHERE workspace_id = ? AND action = ?
                ORDER BY created_at DESC LIMIT 1
                """,
                workspaceId,
                action);
    }
}
