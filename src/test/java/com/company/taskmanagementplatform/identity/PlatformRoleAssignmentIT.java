package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.PlatformRoleService;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * Only a platform-scoped role can reach {@code users.platform_role_id}.
 *
 * <p>Two independent guarantees, tested separately because either one alone would be weaker than it
 * looks. The service surface removes the mistake by construction: the supported entry point takes no
 * role identifier, so naming the wrong role is not expressible. The database backs it up regardless
 * of what any caller does, through a foreign key on {@code (platform_role_id, platform_role_scope)}
 * that a workspace role cannot satisfy.
 */
class PlatformRoleAssignmentIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private UserAccountService users;

    @Autowired
    private PlatformRoleService platformRoles;

    @Test
    void theSupportedEntryPointGrantsTheAdministratorRole() {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("promote"));

        platformRoles.assignSuperAdmin(person.id());

        assertThat(users.findById(person.id()).orElseThrow().platformRoleId())
                .isEqualTo(platformRoles.superAdminRoleId());
    }

    @Test
    void grantingAlsoWritesTheScopeSoTheKeyIsChecked() {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("scope-written"));

        platformRoles.assignSuperAdmin(person.id());

        String scope = jdbc.queryForObject(
                "SELECT platform_role_scope FROM users WHERE id = ?", String.class, person.id());

        assertThat(scope).isEqualTo("PLATFORM");
    }

    @Test
    void theWriterRefusesAWorkspaceRoleEvenWhenCalledDirectly() {
        // The service surface makes this unreachable in ordinary code. Calling the
        // writer straight is the only way to attempt it, and the database still
        // refuses, which is the guarantee that does not depend on anybody's care.
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Scoped", uniqueSlug("scoped"), owner.id());
        UUID workspaceRoleId = fixtures.roleId(workspace.id(), "ADMIN");

        UserAccount victim = fixtures.verifiedUser(uniqueEmail("victim"));

        assertThatThrownBy(() -> users.assignPlatformRole(victim.id(), workspaceRoleId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theWriterRefusesARoleThatDoesNotExist() {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("ghost-role"));

        assertThatThrownBy(() -> users.assignPlatformRole(person.id(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theColumnPairCannotBeSplitBySql() {
        // The pair check is what stops the foreign key being skipped: a key with a
        // null column is not checked at all, so a role id with no scope beside it
        // would have been a way past the whole arrangement.
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Split", uniqueSlug("split"), owner.id());
        UUID workspaceRoleId = fixtures.roleId(workspace.id(), "ADMIN");
        UserAccount victim = fixtures.verifiedUser(uniqueEmail("split-victim"));

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE users SET platform_role_id = ? WHERE id = ?", workspaceRoleId, victim.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theScopeColumnCannotHoldAnythingButPlatform() {
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Forged", uniqueSlug("forged"), owner.id());
        UUID workspaceRoleId = fixtures.roleId(workspace.id(), "ADMIN");
        UserAccount victim = fixtures.verifiedUser(uniqueEmail("forge-victim"));

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE users SET platform_role_id = ?, platform_role_scope = 'WORKSPACE' WHERE id = ?",
                        workspaceRoleId,
                        victim.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAccountWithNoPlatformRoleCarriesNeitherColumn() {
        UserAccount person = fixtures.verifiedUser(uniqueEmail("ordinary"));

        assertThatCode(() -> jdbc.queryForObject(
                        "SELECT platform_role_scope FROM users WHERE id = ? AND platform_role_id IS NULL",
                        String.class,
                        person.id()))
                .doesNotThrowAnyException();
    }

    // --- phase nine: the role becomes grantable over HTTP ---------------------
    //
    // Until the admin panel there was no endpoint for any of this, so an
    // installation could not promote a second platform administrator without a
    // redeploy. These four cover the new surface and the refusals that guard it.

    @Test
    void theRoleCanBeGrantedAndRevokedOverHttp() throws Exception {
        UserAccount granter = fixtures.superAdmin(uniqueEmail("granter"));
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("subject"));
        String bearer = fixtures.bearer(granter.id());

        mockMvc.perform(put("/api/v1/users/" + subject.id() + "/platform-role")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        assertThat(users.findById(subject.id()).orElseThrow().platformRoleId()).isNotNull();

        mockMvc.perform(delete("/api/v1/users/" + subject.id() + "/platform-role")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        assertThat(users.findById(subject.id()).orElseThrow().platformRoleId()).isNull();
    }

    @Test
    void aNewlyPromotedAdministratorReachesThePlatformEndpointsAtOnce() throws Exception {
        UserAccount granter = fixtures.superAdmin(uniqueEmail("granter"));
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("promoted"));

        // Before: no platform role, so no reach.
        mockMvc.perform(get("/api/v1/admin/statistics")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(subject.id())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/users/" + subject.id() + "/platform-role")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(granter.id())))
                .andExpect(status().isOk());

        // After: the same token, the next request. Nothing is cached anywhere in
        // the authorization path, so there is nothing to invalidate.
        mockMvc.perform(get("/api/v1/admin/statistics")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(subject.id())))
                .andExpect(status().isOk());
    }

    @Test
    void youCannotRevokeYourOwnRole() throws Exception {
        UserAccount administrator = fixtures.superAdmin(uniqueEmail("self-revoke"));

        mockMvc.perform(delete("/api/v1/users/" + administrator.id() + "/platform-role")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(administrator.id())))
                .andExpect(status().isConflict());
    }

    @Test
    void oneOfSeveralAdministratorsMayBeDemoted() {
        // The last-administrator refusal cannot be exercised here: the suite shares
        // one container and one SUPER_ADMIN role, so other tests' administrators
        // are always around and the count is never one. It is pinned in
        // UserAccountServiceTest instead, where the holder count is controllable.
        //
        // What this asserts is the other side, which only real data can show: while
        // more than one exists, demoting any of them is ordinary administration.
        UserAccount granter = fixtures.superAdmin(uniqueEmail("keeps-role"));
        UserAccount spare = fixtures.superAdmin(uniqueEmail("spare"));

        assertThat(users.countHoldersOfPlatformRole(platformRoles.superAdminRoleId()))
                .isGreaterThan(1);

        assertThatCode(() -> platformRoles.revokeSuperAdmin(spare.id())).doesNotThrowAnyException();
        assertThat(users.findById(spare.id()).orElseThrow().platformRoleId()).isNull();
        assertThat(users.findById(granter.id()).orElseThrow().platformRoleId()).isNotNull();
    }

    @Test
    void aWorkspaceMemberStillCannotHoldThePlatformRole() {
        // The other half of the rule, enforced by a different key. Neither route in.
        UserAccount owner = fixtures.superAdmin(uniqueEmail("owner"));
        WorkspaceResponse workspace = fixtures.workspace("Both", uniqueSlug("both"), owner.id());
        UserAccount member = fixtures.verifiedUser(uniqueEmail("member"));

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO workspace_members (workspace_id, user_id, role_id) VALUES (?, ?, ?)",
                        workspace.id(),
                        member.id(),
                        platformRoles.superAdminRoleId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
