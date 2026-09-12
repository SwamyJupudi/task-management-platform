package com.company.taskmanagementplatform.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The role editor: the thing {@code RoleQueryService} has been saying belongs to the admin panel
 * since phase two.
 *
 * <p>{@code role:manage} has sat in the catalog, granted to {@code ADMIN}, with nothing checking it
 * for seven phases. Turning it on widens what a workspace administrator can do without any grant
 * changing, which is a behaviour change rather than a new feature.
 *
 * <p>The editor replaces the whole set rather than applying a delta, for the reason the label editor
 * gave first: a delta needs the client to know the current state to compute it, and two
 * administrators editing one role would merge into a set neither chose.
 */
class RoleAdministrationIT extends AdminApiTestBase {

    @Test
    void replacesTheWholeSetAndReturnsTheRoleAsItNowStands() throws Exception {
        Estate estate = estate();

        mockMvc.perform(edit(estate, "EMPLOYEE", "[\"task:read\",\"workspace:read\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("EMPLOYEE"))
                .andExpect(jsonPath("$.permissions.length()").value(2));

        // Codes not listed are removed. The employee role arrives with thirteen.
        mockMvc.perform(get("/api/v1/workspaces/" + estate.firstWorkspaceId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.firstAdminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'EMPLOYEE')].permissions.length()").value(2));
    }

    @Test
    void anEmptyListIsValidAndMeansTheRoleGrantsNothing() throws Exception {
        Estate estate = estate();

        mockMvc.perform(edit(estate, "EMPLOYEE", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(0));
    }

    @Test
    void anOmittedListIsRefused() throws Exception {
        Estate estate = estate();

        // Distinct from an empty one. "Grant nothing" is a decision; omitting the
        // field is a malformed request.
        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "EMPLOYEE"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownCodeIsRefusedAndNothingIsWritten() throws Exception {
        Estate estate = estate();

        mockMvc.perform(edit(estate, "EMPLOYEE", "[\"task:read\",\"task:teleport\"]"))
                .andExpect(status().isBadRequest());

        // Every code is checked before anything is written, so the role is exactly
        // as it was rather than half replaced.
        mockMvc.perform(get("/api/v1/workspaces/" + estate.firstWorkspaceId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.firstAdminId())))
                .andExpect(jsonPath("$[?(@.slug == 'EMPLOYEE')].permissions.length()").value(13));
    }

    @Test
    void duplicatesAreFoldedRatherThanRefused() throws Exception {
        Estate estate = estate();

        mockMvc.perform(edit(estate, "EMPLOYEE", "[\"task:read\",\"task:read\",\"workspace:read\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2));
    }

    @Test
    void aRoleOfAnotherWorkspaceIsNotFound() throws Exception {
        Estate estate = estate();

        // Roles are looked up by the pair, so one workspace can never reach
        // another's. The first administrator has nothing to do with the second
        // workspace, so it answers as missing rather than as forbidden.
        mockMvc.perform(put(rolePermissionsPath(estate.secondWorkspaceId(), "EMPLOYEE"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.firstAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theSuperAdminRoleCannotBeReachedThroughAnyWorkspacePath() throws Exception {
        Estate estate = estate();

        // Not a service check: a platform role has a null workspace and so matches
        // no (workspaceId, slug) pair. The schema is what makes this unreachable,
        // the same way it stops a member being assigned the platform role.
        mockMvc.perform(edit(estate, "SUPER_ADMIN", "[]")).andExpect(status().isNotFound());
    }

    @Test
    void anArchivedWorkspaceIsFrozenAgainstRoleEdits() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(post("/api/v1/workspaces/" + estate.firstWorkspaceId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());

        // Editing the roles of a frozen workspace is a change like any other, which
        // is why the editor asks requirePermissionToChange rather than
        // requirePermission.
        mockMvc.perform(edit(estate, "EMPLOYEE", "[]")).andExpect(status().isConflict());
    }

    @Test
    void anEmployeeCannotEditRolesAndAnAdministratorCan() throws Exception {
        Estate estate = estate();
        UUID employee = member(estate, "EMPLOYEE");

        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "TEAM_LEAD"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"task:read\"]}"))
                .andExpect(status().isForbidden());

        // Same request, same workspace, different role. role:manage is the whole
        // difference, and it was already granted to ADMIN seven phases ago.
        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "TEAM_LEAD"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.firstAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"task:read\"]}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder edit(
            Estate estate, String roleSlug, String permissionsJson) {
        return put(rolePermissionsPath(estate.firstWorkspaceId(), roleSlug))
                .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":" + permissionsJson + "}");
    }
}
