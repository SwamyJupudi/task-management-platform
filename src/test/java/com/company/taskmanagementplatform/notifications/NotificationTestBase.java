package com.company.taskmanagementplatform.notifications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * What the notification tests share: the two actions with no fixture, and a way to read the table.
 *
 * <p>Named so that neither Surefire nor Failsafe collects it, exactly as {@code TaskApiTestBase} and
 * {@code AbstractCollaborationIT} are.
 *
 * <p>Every assertion about a written row goes through {@link #eventually}, because notifications are
 * written after commit on the module's own thread. A test that asserted immediately would be
 * asserting against a race and would fail on a loaded machine rather than on a defect.
 */
abstract class NotificationTestBase extends AbstractCollaborationIT {

    @Autowired
    protected JdbcTemplate jdbc;

    /** Assigning has no fixture, so it goes through the API, which also commits it. */
    protected void assign(Scene scene, UUID taskId, UUID assigneeUserId, UUID actorUserId) throws Exception {
        mockMvc.perform(put(workspacePath(scene) + "/tasks/" + taskId + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(actorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("assigneeUserId", assigneeUserId))))
                .andExpect(status().isOk());
    }

    protected void changeProjectStatus(Scene scene, String status, UUID actorUserId) throws Exception {
        mockMvc.perform(post(workspacePath(scene) + "/projects/" + scene.projectId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(actorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    /** A task with a due date, which the create fixture does not offer. */
    protected TaskResponse taskDueOn(Scene scene, String title, LocalDate dueDate, UUID assigneeUserId) {
        return taskFixtures.task(
                scene.workspaceId(),
                scene.projectId(),
                new CreateTaskRequest(title, null, assigneeUserId, null, null, null, dueDate, null, null, null),
                scene.adminId());
    }

    protected String notificationsPath(Scene scene) {
        return workspacePath(scene) + "/notifications";
    }

    /**
     * A project member whose own joining notification has already arrived and been read.
     *
     * <p>Adding somebody to a project is itself one of the six triggers, so every recipient created
     * by {@code projectMember} starts with one unread notification. A test about read state that
     * ignored it would be asserting against a race between its setup and its subject. This waits for
     * that row and clears it, so the test starts from an empty badge.
     */
    protected UUID settledProjectMember(Scene scene, String roleSlug) throws Exception {
        UUID userId = projectMember(scene, roleSlug);

        eventually(() -> org.assertj.core.api.Assertions.assertThat(countFor(userId, "project.member_added"))
                .isEqualTo(1));
        mockMvc.perform(post(notificationsPath(scene) + "/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isOk());

        return userId;
    }

    /** The types one person was told about, oldest first, read straight from the table. */
    protected List<String> typesFor(UUID recipientUserId) {
        return jdbc.queryForList(
                "SELECT type FROM notifications WHERE recipient_user_id = ? ORDER BY created_at",
                String.class,
                recipientUserId);
    }

    protected List<Map<String, Object>> rowsFor(UUID recipientUserId) {
        return jdbc.queryForList(
                "SELECT * FROM notifications WHERE recipient_user_id = ? ORDER BY created_at", recipientUserId);
    }

    protected Map<String, Object> latestFor(UUID recipientUserId, String type) {
        return jdbc.queryForMap(
                """
                SELECT * FROM notifications
                WHERE recipient_user_id = ? AND type = ?
                ORDER BY created_at DESC LIMIT 1
                """,
                recipientUserId,
                type);
    }

    protected int countFor(UUID recipientUserId, String type) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_user_id = ? AND type = ?",
                Integer.class,
                recipientUserId,
                type);
        return count == null ? 0 : count;
    }
}
