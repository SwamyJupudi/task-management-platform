package com.company.taskmanagementplatform.support;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.tasks.TaskRef;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * The scaffolding the comment, attachment and activity tests share: a workspace, an administrator, a
 * project, and one task inside it.
 *
 * <p>The same idea as {@code TaskApiTestBase}, one level down and public, because the three modules
 * of this phase live in three packages and all of them need the same five lines of setup. Not itself
 * a test, and named so that neither Surefire nor Failsafe collects it.
 */
public abstract class AbstractCollaborationIT extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected IdentityFixtures fixtures;

    @Autowired
    protected TaskFixtures taskFixtures;

    @Autowired
    protected CollaborationFixtures collaboration;

    /** A workspace with an administrator, one project they own, and one task on it. */
    protected Scene scene() {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));
        WorkspaceResponse workspace = fixtures.workspace("Collab", uniqueSlug("collab"), platform.id());

        UserAccount admin = fixtures.verifiedUser(uniqueEmail("admin"));
        fixtures.addMember(workspace.id(), admin.id(), "ADMIN");

        String key = uniqueKey();
        ProjectResponse project = taskFixtures.project(workspace.id(), key, admin.id());
        TaskResponse task = taskFixtures.task(workspace.id(), project.id(), "Build authentication", admin.id());

        return new Scene(workspace.id(), admin.id(), project.id(), key, task.id());
    }

    /** Somebody in the workspace holding the named role, and not on any project. */
    protected UUID member(Scene scene, String roleSlug) {
        UserAccount person = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(Locale.ROOT)));
        fixtures.addMember(scene.workspaceId(), person.id(), roleSlug);
        return person.id();
    }

    /** The same, put on the scene's project. */
    protected UUID projectMember(Scene scene, String roleSlug) {
        UUID userId = member(scene, roleSlug);
        taskFixtures.addProjectMember(scene.workspaceId(), scene.projectId(), userId, scene.adminId());
        return userId;
    }

    protected String bearer(UUID userId) {
        return fixtures.bearer(userId);
    }

    protected TaskRef ref(Scene scene) {
        return new TaskRef(scene.taskId(), scene.projectId(), scene.workspaceId());
    }

    protected static String workspacePath(Scene scene) {
        return "/api/v1/workspaces/" + scene.workspaceId();
    }

    protected static String taskPath(Scene scene) {
        return workspacePath(scene) + "/tasks/" + scene.taskId();
    }

    protected static String commentsPath(Scene scene) {
        return taskPath(scene) + "/comments";
    }

    protected static String commentPath(Scene scene, UUID commentId) {
        return workspacePath(scene) + "/comments/" + commentId;
    }

    protected static String attachmentsPath(Scene scene) {
        return taskPath(scene) + "/attachments";
    }

    protected static String attachmentPath(Scene scene, UUID attachmentId) {
        return workspacePath(scene) + "/attachments/" + attachmentId;
    }

    protected static String uniqueKey() {
        return "C" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /**
     * Retries an assertion until it holds, for anything the audit trail writes.
     *
     * <p>Activity rows are written on the activity module's own thread a moment after the
     * transaction commits, which is what stops a writing request from holding two database
     * connections at once. A test that asserted immediately would be asserting against a race, and
     * would fail on a loaded machine rather than on a defect.
     *
     * <p>Deliberately not a sleep. A fixed pause is either too short on a slow machine or wasted
     * time on a fast one, and this returns as soon as the row is there.
     */
    protected static void eventually(ThrowingAssertion assertion) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Throwable lastFailure = null;

        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError | Exception e) {
                // A missing row fails as an exception from the query rather than as
                // an assertion, and that is exactly the case being waited for, so
                // both are retried. Whatever failed last is rethrown at the deadline.
                lastFailure = e;
            }

            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Still failing after 10s of retries", lastFailure);
            }
            sleepBriefly();
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** An assertion that may throw, so a MockMvc call can be retried like any other. */
    @FunctionalInterface
    protected interface ThrowingAssertion {
        void run() throws Exception;
    }

    /** One workspace, its administrator, one project and one task. */
    public record Scene(UUID workspaceId, UUID adminId, UUID projectId, String projectKey, UUID taskId) {}
}
