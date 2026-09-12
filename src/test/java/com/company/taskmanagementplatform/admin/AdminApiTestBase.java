package com.company.taskmanagementplatform.admin;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.TaskFixtures;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * The scaffolding the admin panel tests share.
 *
 * <p>Not itself a test, and named so that neither Surefire nor Failsafe collects it.
 *
 * <p>The one thing this base does that no other test base in the platform does is build
 * <strong>two</strong> workspaces. Every figure the admin panel produces crosses workspaces, so a
 * fixture confined to one would let a query that accidentally carried a tenant predicate pass
 * silently: it would simply read low, and a low number is not a failure.
 *
 * <p>Everything is built through the ordinary services, as {@code TaskFixtures} explains, with one
 * inherited exception: {@code AdminSchemaIT} writes SQL, because the schema is what it is about.
 */
abstract class AdminApiTestBase extends AbstractIntegrationTest {

    protected static final String STATISTICS = "/api/v1/admin/statistics";
    protected static final String PLATFORM_ACTIVITY = "/api/v1/admin/activity";
    protected static final String PLATFORM_PROJECTS = "/api/v1/admin/projects";
    protected static final String PLATFORM_ACCOUNTS = "/api/v1/admin/accounts";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected IdentityFixtures fixtures;

    @Autowired
    protected TaskFixtures taskFixtures;

    /**
     * Two workspaces, each with its own administrator and project, and one platform administrator
     * who belongs to neither.
     *
     * <p>That last part matters. A platform administrator holds no membership row anywhere, by the
     * decision {@code WorkspaceProvisioningService} records, so every admin endpoint they reach is
     * reached through the platform role alone. If any of them accidentally consulted membership, the
     * caller would have none and the test would fail loudly rather than quietly.
     */
    protected Estate estate() {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));

        WorkspaceResponse first = fixtures.workspace("First", uniqueSlug("first"), platform.id());
        UserAccount firstAdmin = fixtures.verifiedUser(uniqueEmail("first-admin"));
        fixtures.addMember(first.id(), firstAdmin.id(), "ADMIN");
        ProjectResponse firstProject = taskFixtures.project(first.id(), uniqueKey(), firstAdmin.id());

        WorkspaceResponse second = fixtures.workspace("Second", uniqueSlug("second"), platform.id());
        UserAccount secondAdmin = fixtures.verifiedUser(uniqueEmail("second-admin"));
        fixtures.addMember(second.id(), secondAdmin.id(), "ADMIN");
        ProjectResponse secondProject = taskFixtures.project(second.id(), uniqueKey(), secondAdmin.id());

        return new Estate(
                platform.id(),
                first.id(),
                firstAdmin.id(),
                firstProject.id(),
                second.id(),
                secondAdmin.id(),
                secondProject.id());
    }

    /** Somebody in the first workspace holding the named role. */
    protected UUID member(Estate estate, String roleSlug) {
        UserAccount person = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(Locale.ROOT)));
        fixtures.addMember(estate.firstWorkspaceId(), person.id(), roleSlug);
        return person.id();
    }

    protected TaskResponse taskDue(Estate estate, UUID projectId, String title, LocalDate dueDate, UUID actorId) {
        return taskFixtures.task(
                projectId.equals(estate.firstProjectId()) ? estate.firstWorkspaceId() : estate.secondWorkspaceId(),
                projectId,
                new CreateTaskRequest(title, null, null, null, null, null, dueDate, null, null, null),
                actorId);
    }

    protected String bearer(UUID userId) {
        return fixtures.bearer(userId);
    }

    protected static String uniqueKey() {
        return "K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    protected static String rolePermissionsPath(UUID workspaceId, String roleSlug) {
        return "/api/v1/workspaces/" + workspaceId + "/roles/" + roleSlug + "/permissions";
    }

    /**
     * Retries an assertion until it holds or the deadline passes.
     *
     * <p>Audit rows are written after the publishing transaction commits, on the activity module's
     * own thread, so an assertion made immediately is racing a queue. The same helper exists on
     * {@code AbstractCollaborationIT} for the same reason; it is repeated here rather than hoisted
     * into the shared base, which sixty tests extend and none of the others need it.
     *
     * <p>Both an assertion failure and an exception are retried, because a row that has not arrived
     * yet fails as an empty result rather than as a failed comparison.
     */
    protected static void eventually(ThrowingAssertion assertion) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        Throwable lastFailure = null;

        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError | Exception e) {
                lastFailure = e;
            }

            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Still failing after 10s of retries", lastFailure);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", e);
            }
        }
    }

    @FunctionalInterface
    protected interface ThrowingAssertion {
        void run() throws Exception;
    }

    /**
     * @param platformAdminId holds the platform role and belongs to no workspace
     * @param firstAdminId administers the first workspace and has no platform role at all
     */
    protected record Estate(
            UUID platformAdminId,
            UUID firstWorkspaceId,
            UUID firstAdminId,
            UUID firstProjectId,
            UUID secondWorkspaceId,
            UUID secondAdminId,
            UUID secondProjectId) {}
}
