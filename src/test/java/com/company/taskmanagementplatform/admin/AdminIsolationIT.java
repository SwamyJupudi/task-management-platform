package com.company.taskmanagementplatform.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The test to keep honest in this phase, and the one whose failure would be widest.
 *
 * <p>Every other authorization test in this platform guards one workspace. {@code
 * ProjectVisibilityIT} and {@code TaskVisibilityIT} stop somebody seeing work inside a workspace
 * they are in; {@code ReportVisibilityIT} stops a number computed over it. This one stops an
 * administrator of one workspace reaching <strong>every workspace in the installation</strong>, and
 * a mistake in it would not throw or return the wrong status. It would return a plausible number
 * computed over the whole company.
 *
 * <p>The design that makes it hold is one line: an admin endpoint is gated by {@code
 * @perm.onPlatform} and nothing else, and that resolver never consults workspace membership. So
 * every case below holds <strong>the widest possible workspace grants</strong> and differs from the
 * passing case only in whether the caller has a platform role. Nothing here passes or fails because
 * of a workspace permission difference, which is the whole point.
 *
 * <p>It walks every route rather than a sample, deliberately. A route added later and forgotten here
 * is a route nothing protects, and {@code ProtectedRouteMatrixIT} only proves it refuses an
 * anonymous caller, not that it refuses a signed-in one who administers somewhere else.
 */
class AdminIsolationIT extends AdminApiTestBase {

    @Test
    void aWorkspaceAdministratorReachesNoPlatformRouteAtAll() throws Exception {
        Estate estate = estate();
        // Holds every workspace permission there is, in a workspace with real data.
        String workspaceAdmin = bearer(estate.firstAdminId());

        for (MockHttpServletRequestBuilder route : platformRoutes(estate)) {
            mockMvc.perform(route.header(HttpHeaders.AUTHORIZATION, workspaceAdmin))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void aTeamLeadAndAnEmployeeReachNoneOfThemEither() throws Exception {
        Estate estate = estate();

        for (String roleSlug : new String[] {"TEAM_LEAD", "EMPLOYEE"}) {
            String caller = bearer(member(estate, roleSlug));

            for (MockHttpServletRequestBuilder route : platformRoutes(estate)) {
                mockMvc.perform(route.header(HttpHeaders.AUTHORIZATION, caller))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void thePlatformAdministratorReachesEveryReadRoute() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        // The other half of the matrix. Without it, a mistake that refused
        // everybody would look like a passing isolation test.
        mockMvc.perform(get(STATISTICS).header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        mockMvc.perform(get(PLATFORM_ACTIVITY).header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        mockMvc.perform(get(PLATFORM_PROJECTS).header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        mockMvc.perform(get(PLATFORM_ACCOUNTS).header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
    }

    @Test
    void administeringOneWorkspaceIsNotAdministeringAnother() throws Exception {
        Estate estate = estate();
        String firstAdmin = bearer(estate.firstAdminId());

        // The narrower half of the same rule: a workspace administrator's reach
        // stops at their own workspace's edge, and a workspace they have nothing
        // to do with answers as missing rather than as forbidden.
        mockMvc.perform(get("/api/v1/workspaces/" + estate.secondWorkspaceId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, firstAdmin))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(rolePermissionsPath(estate.secondWorkspaceId(), "EMPLOYEE"))
                        .header(HttpHeaders.AUTHORIZATION, firstAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWorkspaceAdministratorCannotEditTheRolesOfAWorkspaceTheyDoNotAdminister() throws Exception {
        Estate estate = estate();
        // An employee of the first workspace: can see it, may not edit its roles.
        String employee = bearer(member(estate, "EMPLOYEE"));

        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "EMPLOYEE"))
                        .header(HttpHeaders.AUTHORIZATION, employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"task:read\"]}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Every platform-scoped route this phase adds.
     *
     * <p>Kept in one place so a new route joins every case above at once. The account routes are here
     * as well as the {@code /admin} ones, because they are platform-scoped for the same reason and a
     * caller who could reach them would be administering accounts across the installation.
     */
    private MockHttpServletRequestBuilder[] platformRoutes(Estate estate) {
        UUID subject = estate.secondAdminId();

        return new MockHttpServletRequestBuilder[] {
            get(STATISTICS),
            get(PLATFORM_ACTIVITY),
            get(PLATFORM_PROJECTS),
            get(PLATFORM_ACCOUNTS),
            patch("/api/v1/users/" + subject)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"firstName\":\"Taken\",\"lastName\":\"Over\"}"),
            post("/api/v1/users/" + subject + "/unlock"),
            post("/api/v1/users/" + subject + "/password-reset"),
            post("/api/v1/users/" + subject + "/resend-verification"),
            put("/api/v1/users/" + subject + "/platform-role"),
            delete("/api/v1/users/" + subject + "/platform-role"),
        };
    }
}
