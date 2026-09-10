package com.company.taskmanagementplatform.workspaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.common.security.Permissions;

class SystemRoleTest {

    @Test
    void everySeededRoleGrantsOnlyPermissionsThatExist() {
        // A role granting a code with no row behind it would be a role that cannot
        // do its job, and nothing else would notice until somebody was refused.
        for (SystemRole role : SystemRole.values()) {
            assertThat(Permissions.ALL)
                    .as("permissions granted by %s", role)
                    .containsAll(role.permissionCodes());
        }
    }

    @Test
    void anAdministratorCanRunTheirWorkspace() {
        assertThat(SystemRole.ADMIN.permissionCodes())
                .contains(
                        Permissions.MEMBER_INVITE,
                        Permissions.MEMBER_REMOVE,
                        Permissions.MEMBER_ASSIGN_ROLE,
                        Permissions.ROLE_MANAGE);
    }

    @Test
    void aTeamLeadCanSeeTheRosterButNotChangeIt() {
        assertThat(SystemRole.TEAM_LEAD.permissionCodes()).contains(Permissions.MEMBER_READ);
        assertThat(SystemRole.TEAM_LEAD.permissionCodes())
                .doesNotContain(
                        Permissions.MEMBER_INVITE, Permissions.MEMBER_REMOVE, Permissions.MEMBER_ASSIGN_ROLE);
    }

    @Test
    void aTeamLeadManagesTeamsWithoutTheWorkspaceWideGrant() {
        // The scope half of the two-layer rule. Holding team:update says what they
        // may do; withholding team:manage_any is what narrows it to the teams they
        // actually lead. Granting both here would silently widen every lead in
        // every workspace to every team, and no other test would notice.
        assertThat(SystemRole.TEAM_LEAD.permissionCodes())
                .contains(Permissions.TEAM_READ, Permissions.TEAM_UPDATE, Permissions.TEAM_MANAGE_MEMBERS);

        assertThat(SystemRole.TEAM_LEAD.permissionCodes())
                .doesNotContain(Permissions.TEAM_MANAGE_ANY, Permissions.TEAM_CREATE, Permissions.TEAM_DELETE);
    }

    @Test
    void anAdministratorRunsEveryTeamInTheirWorkspace() {
        assertThat(SystemRole.ADMIN.permissionCodes())
                .contains(
                        Permissions.TEAM_CREATE,
                        Permissions.TEAM_UPDATE,
                        Permissions.TEAM_DELETE,
                        Permissions.TEAM_MANAGE_MEMBERS,
                        Permissions.TEAM_MANAGE_ANY,
                        Permissions.WORKSPACE_ARCHIVE);
    }

    @Test
    void anEmployeeMaySeeTeamsAndChangeNone() {
        assertThat(SystemRole.EMPLOYEE.permissionCodes()).contains(Permissions.TEAM_READ);
        assertThat(SystemRole.EMPLOYEE.permissionCodes())
                .doesNotContain(
                        Permissions.TEAM_CREATE,
                        Permissions.TEAM_UPDATE,
                        Permissions.TEAM_DELETE,
                        Permissions.TEAM_MANAGE_MEMBERS,
                        Permissions.TEAM_MANAGE_ANY);
    }

    @Test
    void everyRoleCanSeeTheTeamsOfItsWorkspace() {
        // Teams are the shape of the workspace. A role that could not see them
        // would be looking at an installation with a hole in the middle of it.
        for (SystemRole role : SystemRole.values()) {
            assertThat(role.permissionCodes())
                    .as("permissions granted by %s", role)
                    .contains(Permissions.TEAM_READ);
        }
    }

    @Test
    void anEmployeeHoldsNothingAdministrative() {
        assertThat(SystemRole.EMPLOYEE.permissionCodes())
                .doesNotContain(
                        Permissions.MEMBER_INVITE,
                        Permissions.MEMBER_REMOVE,
                        Permissions.MEMBER_ASSIGN_ROLE,
                        Permissions.ROLE_MANAGE,
                        Permissions.USER_DEACTIVATE);
    }

    @Test
    void everyRoleCanAtLeastSeeItsWorkspace() {
        // The access guard treats an empty permission set as no relationship at all
        // and answers 404. A role granting nothing would make its own members
        // invisible to themselves.
        for (SystemRole role : SystemRole.values()) {
            assertThat(role.permissionCodes())
                    .as("permissions granted by %s", role)
                    .contains(Permissions.WORKSPACE_READ);
        }
    }

    @Test
    void noWorkspaceRoleGrantsPlatformAdministration() {
        Set<String> everythingWorkspaceRolesGrant = Arrays.stream(SystemRole.values())
                .flatMap(role -> role.permissionCodes().stream())
                .collect(Collectors.toSet());

        assertThat(everythingWorkspaceRolesGrant)
                .doesNotContain(
                        Permissions.WORKSPACE_CREATE,
                        // Removing a workspace stays platform administration even
                        // though archiving one does not.
                        Permissions.WORKSPACE_DELETE,
                        Permissions.USER_DELETE,
                        Permissions.USER_DEACTIVATE,
                        Permissions.USER_ACTIVATE);
    }

    @Test
    void theSuperAdminSlugIsNotAWorkspaceRole() {
        // It is platform-scoped, has no workspace, and is seeded by a migration.
        assertThat(Arrays.stream(SystemRole.values()).map(SystemRole::slug))
                .doesNotContain(SystemRole.SUPER_ADMIN_SLUG);
    }
}
