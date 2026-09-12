package com.company.taskmanagementplatform.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * That the role editor cannot be used to lock a workspace out of its own administration, and that a
 * role edit takes effect at once.
 *
 * <p>The refusal is deliberately narrow: it applies only when the role being edited is the caller's
 * own role in that workspace. A workspace administrator who removes {@code role:manage} from their
 * own role could never put it back, and only the platform administrator could repair it. An
 * installation should not have to call them to undo a typo.
 *
 * <p>It does not attempt the global rule that some role somewhere must retain the grant. That one
 * needs a holder count on every edit and refuses legitimate changes to roles nobody holds.
 */
class RoleLockoutIT extends AdminApiTestBase {

    @Test
    void anAdministratorCannotRemoveRoleManagementFromTheirOwnRole() throws Exception {
        Estate estate = estate();

        // Everything an ADMIN has except role:manage. The one code that matters.
        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "ADMIN"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.firstAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"workspace:read\",\"member:read\",\"role:read\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anAdministratorMayEditAnotherRoleFreely() throws Exception {
        Estate estate = estate();

        // The rule is about the caller's own role, not about role:manage in
        // general. Narrowing somebody else's role is ordinary administration.
        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "EMPLOYEE"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.firstAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"workspace:read\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void thePlatformAdministratorIsUnaffectedBecauseItHoldsNoWorkspaceRole() throws Exception {
        Estate estate = estate();

        // It edits through its platform role and holds no membership anywhere, so
        // there is no "own role" here and the rule does not apply. That is correct
        // rather than an oversight: it is precisely the repair path the rule exists
        // to avoid needing.
        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "ADMIN"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"workspace:read\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void anEditTakesEffectOnTheNextRequestWithNoRestartAndNoCacheToFlush() throws Exception {
        Estate estate = estate();
        UUID employee = member(estate, "EMPLOYEE");
        String caller = bearer(employee);

        // Reading the workspace needs workspace:read, which EMPLOYEE holds.
        mockMvc.perform(get("/api/v1/workspaces/" + estate.firstWorkspaceId())
                        .header(HttpHeaders.AUTHORIZATION, caller))
                .andExpect(status().isOk());

        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "EMPLOYEE"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"task:read\"]}"))
                .andExpect(status().isOk());

        // The same token, the next request. This is the standing no-cache decision
        // paying off: there is nothing to invalidate, on any instance.
        //
        // 403 and not 404: the role still grants task:read, so the caller has a
        // relationship with the workspace and it is right to admit it exists. Had
        // the role been emptied entirely, the guard would answer 404 instead.
        mockMvc.perform(get("/api/v1/workspaces/" + estate.firstWorkspaceId())
                        .header(HttpHeaders.AUTHORIZATION, caller))
                .andExpect(status().isForbidden());
    }
}
