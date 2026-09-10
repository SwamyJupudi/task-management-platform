package com.company.taskmanagementplatform.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * The generated API document describes what the identity phase actually exposes.
 *
 * <p>The document is generated from the controllers, so it cannot drift from the code, but the
 * security scheme and the default requirement are configured by hand and can. The requirements ask
 * for documentation maintained throughout development, and a document that omits how to authenticate
 * is not that.
 */
class OpenApiContractIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void declaresTheBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void appliesTheSchemeByDefaultSoAnOmissionLocksRatherThanOpens() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    void documentsTheIdentityEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password/forgot'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password/reset'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password/change'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/permissions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/invitations/accept'].post").exists());
    }

    @Test
    void documentsTheWorkspaceLifecycleEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/unarchive'].post").exists());
    }

    @Test
    void documentsTheTeamEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}/archive'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}/unarchive'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}/members'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}/members'].post")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}/members/{userId}'].delete")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}/lead'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/teams/{teamId}/lead'].delete")
                        .exists());
    }

    @Test
    void documentsTheProjectEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}'].patch")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}'].delete")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/status'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/members'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/members'].post")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/members/{userId}'].delete")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/owner'].put")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/owner'].delete")
                        .exists());
    }

    @Test
    void documentsTheTaskEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/status'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/assignee'].put")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/assignee'].delete")
                        .exists());
    }

    @Test
    void documentsTheSubtaskAndDependencyEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks'].post")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks/{subtaskId}'].patch")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks/{subtaskId}'].delete")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks/{subtaskId}/status'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dependencies'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dependencies'].post")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dependencies/{dependsOnTaskId}'].delete")
                        .exists());
    }

    @Test
    void keepsTheSharedErrorShapeInTheDocument() throws Exception {
        // The foundation's promise: one error body across every endpoint.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiError").exists());
    }

    @Test
    void neverDescribesTheRefreshTokenAsAnInput() throws Exception {
        // It is set and read by the server. Documenting it as a parameter would
        // invite a client to try to supply one.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.parameters").doesNotExist());
    }
}
