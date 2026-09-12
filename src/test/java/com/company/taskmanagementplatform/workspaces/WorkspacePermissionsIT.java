package com.company.taskmanagementplatform.workspaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * What the caller may do in one workspace, answered for the caller and for nobody else.
 *
 * <p>This endpoint exists because the interface has to decide what to render before it knows whether
 * a request would be refused, and nothing else could tell it. {@code /auth/me} carries the platform
 * permissions and the caller's role slug per workspace but deliberately not the workspace codes;
 * {@code /workspaces/{id}/roles} carries them and needs {@code role:read}, which an employee does
 * not hold. So the one caller who most needs to know what they may do was the one caller unable to
 * ask.
 *
 * <p>Lives in this package because {@code SystemRole} is package-private and the assertions below
 * compare against it directly. Comparing against the enum rather than a copied list is the point:
 * these tests fail if the endpoint and the seeded grants ever disagree.
 *
 * <p>The last test is the one that justifies the whole endpoint. A client could otherwise ship a
 * hardcoded role-to-permission map, and phase nine made that wrong by letting an administrator edit
 * what a role grants at runtime.
 */
class WorkspacePermissionsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void anEmployeeIsToldExactlyWhatTheEmployeeRoleGrants() throws Exception {
        Scene scene = scene();
        UUID employee = member(scene, "EMPLOYEE");

        assertThat(permissionsOf(scene, employee))
                .containsExactlyInAnyOrderElementsOf(SystemRole.EMPLOYEE.permissionCodes());
    }

    @Test
    void anAdministratorIsToldTheWiderSet() throws Exception {
        Scene scene = scene();
        UUID admin = member(scene, "ADMIN");

        List<String> granted = permissionsOf(scene, admin);

        assertThat(granted).containsExactlyInAnyOrderElementsOf(SystemRole.ADMIN.permissionCodes());
        // The two codes that most change what an interface draws.
        assertThat(granted).contains(Permissions.ROLE_MANAGE, Permissions.PROJECT_READ_ANY);
    }

    @Test
    void aTeamLeadIsToldTheirOwnSetAndNotTheAdministratorsOne() throws Exception {
        Scene scene = scene();
        UUID lead = member(scene, "TEAM_LEAD");

        List<String> granted = permissionsOf(scene, lead);

        assertThat(granted).containsExactlyInAnyOrderElementsOf(SystemRole.TEAM_LEAD.permissionCodes());
        // The write-scope grants are what separate a lead from an administrator,
        // and an interface that offered them would be offering refused actions.
        assertThat(granted)
                .doesNotContain(
                        Permissions.TEAM_MANAGE_ANY,
                        Permissions.PROJECT_MANAGE_ANY,
                        Permissions.TASK_MANAGE_ANY,
                        Permissions.ROLE_MANAGE);
    }

    @Test
    void theResponseNamesTheWorkspaceAndIsSortedWithoutDuplicates() throws Exception {
        Scene scene = scene();
        UUID employee = member(scene, "EMPLOYEE");

        mockMvc.perform(get(path(scene)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(scene.workspaceId().toString()));

        List<String> granted = permissionsOf(scene, employee);

        // Sorted, so two identical requests produce byte-identical bodies rather
        // than reordering with the hash set behind them.
        assertThat(granted).isSorted();
        assertThat(granted).doesNotHaveDuplicates();
    }

    @Test
    void thePlatformAdministratorIsAnsweredWithoutHoldingAnyMembership() throws Exception {
        Scene scene = scene();

        // It holds no membership row anywhere, by the decision
        // WorkspaceProvisioningService records, so its whole answer comes from the
        // platform role. An endpoint that read the membership first would answer
        // 404 here.
        assertThat(permissionsOf(scene, scene.platformAdminId()))
                .containsExactlyInAnyOrderElementsOf(Permissions.ALL);
    }

    @Test
    void aStrangerIsToldTheWorkspaceIsMissingRatherThanForbidden() throws Exception {
        Scene scene = scene();
        UserAccount outsider = fixtures.verifiedUser(uniqueEmail("outsider"));

        // The tenancy rule, unchanged: a workspace the caller has nothing to do
        // with answers as missing, because a forbidden response would confirm the
        // identifier names something real.
        mockMvc.perform(get(path(scene)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(outsider.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWorkspaceThatDoesNotExistIsAlsoMissing() throws Exception {
        Scene scene = scene();
        UUID employee = member(scene, "EMPLOYEE");

        mockMvc.perform(get("/api/v1/workspaces/" + UUID.randomUUID() + "/me")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employee)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMemberOfOneWorkspaceLearnsNothingAboutAnother() throws Exception {
        Scene first = scene();
        Scene second = scene();
        UUID employeeOfFirst = member(first, "EMPLOYEE");

        mockMvc.perform(get(path(second)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(employeeOfFirst)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anArchivedWorkspaceStillAnswers() throws Exception {
        Scene scene = scene();
        UUID employee = member(scene, "EMPLOYEE");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/workspaces/" + scene.workspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(scene.platformAdminId())))
                .andExpect(status().isOk());

        // Archiving freezes writes; it does not hide the workspace. An interface
        // still has to render it, and still has to know what it may offer, which
        // for an archived workspace is the reads.
        assertThat(permissionsOf(scene, employee)).isNotEmpty();
    }

    @Test
    void theAnswerFollowsARoleEditOnTheVeryNextRequest() throws Exception {
        Scene scene = scene();
        UUID employee = member(scene, "EMPLOYEE");

        assertThat(permissionsOf(scene, employee)).contains(Permissions.TASK_CREATE);

        mockMvc.perform(put("/api/v1/workspaces/" + scene.workspaceId() + "/roles/EMPLOYEE/permissions")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(scene.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"workspace:read\",\"task:read\"]}"))
                .andExpect(status().isOk());

        // The test that justifies the endpoint existing at all. A client could
        // otherwise ship a hardcoded role-to-permission map, and phase nine made
        // that wrong: an administrator can change what a role grants, the mapping
        // is per workspace, and there is no cache anywhere in the resolution path.
        assertThat(permissionsOf(scene, employee))
                .containsExactly(Permissions.TASK_READ, Permissions.WORKSPACE_READ);
    }

    // --- scaffolding --------------------------------------------------------

    private Scene scene() {
        UserAccount platform = fixtures.superAdmin(uniqueEmail("platform"));
        WorkspaceResponse workspace = fixtures.workspace("Perms", uniqueSlug("perms"), platform.id());
        return new Scene(workspace.id(), platform.id());
    }

    private UUID member(Scene scene, String roleSlug) {
        UserAccount person = fixtures.verifiedUser(uniqueEmail(roleSlug.toLowerCase(java.util.Locale.ROOT)));
        fixtures.addMember(scene.workspaceId(), person.id(), roleSlug);
        return person.id();
    }

    private List<String> permissionsOf(Scene scene, UUID userId) throws Exception {
        String body = mockMvc.perform(get(path(scene)).header(HttpHeaders.AUTHORIZATION, fixtures.bearer(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readValue(body, Response.class).permissions();
    }

    private static String path(Scene scene) {
        return "/api/v1/workspaces/" + scene.workspaceId() + "/me";
    }

    /** Read back as a record rather than by json path, so the shape is asserted as well as the values. */
    private record Response(UUID workspaceId, List<String> permissions) {}

    private record Scene(UUID workspaceId, UUID platformAdminId) {}
}
