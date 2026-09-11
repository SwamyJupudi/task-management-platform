package com.company.taskmanagementplatform.activity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.taskmanagementplatform.support.AbstractCollaborationIT;

/**
 * Who may read the audit trail, and the fact that nobody may write to it through the API.
 *
 * <p>The workspace-wide listing is administration and needs {@code activity:read}. One record's
 * history is not: it needs only the ability to see that record, or the people working on a project
 * could not see its own past.
 */
class ActivityAuthorizationIT extends AbstractCollaborationIT {

    @Test
    void anAdministratorMayBrowseTheWorkspaceHistory() throws Exception {
        Scene scene = scene();

        mockMvc.perform(get(workspacePath(scene) + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void anEmployeeMayNot() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        mockMvc.perform(get(workspacePath(scene) + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeamLeadMayNotEither() throws Exception {
        Scene scene = scene();
        UUID lead = projectMember(scene, "TEAM_LEAD");

        mockMvc.perform(get(workspacePath(scene) + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeOnTheProjectMayReadOneTasksHistory() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        mockMvc.perform(get(taskPath(scene) + "/activity").header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk());
    }

    @Test
    void somebodyOffTheProjectReachesNeitherHistory() throws Exception {
        Scene scene = scene();
        UUID outsider = member(scene, "EMPLOYEE");

        mockMvc.perform(get(taskPath(scene) + "/activity").header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(workspacePath(scene) + "/projects/" + scene.projectId() + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWorkspaceTheCallerHasNothingToDoWithIsMissingRatherThanForbidden() throws Exception {
        Scene scene = scene();
        Scene elsewhere = scene();

        mockMvc.perform(get(workspacePath(elsewhere) + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void thereIsNoWayToWriteAnEntryThroughTheApi() throws Exception {
        // The trail is written from events and from nowhere else. A POST to the
        // listing must not be mapped at all.
        Scene scene = scene();

        mockMvc.perform(post(workspacePath(scene) + "/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        Scene scene = scene();

        mockMvc.perform(get(workspacePath(scene) + "/activity")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(taskPath(scene) + "/activity")).andExpect(status().isUnauthorized());
    }
}
