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
