package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * Walks every endpoint the application maps and asserts that anything not on the public list refuses
 * an anonymous caller.
 *
 * <p>This is the test that earns its keep over time. Individual endpoint tests check the endpoints
 * somebody remembered to write a test for; this one checks the ones they did not. A new controller
 * added in a later phase joins it automatically, and the only way to leave a route open is to add it
 * to the list below, deliberately, in a review.
 */
class ProtectedRouteMatrixIT extends AbstractIntegrationTest {

    /**
     * Every route reachable without a token, and why.
     *
     * <p>Each entry is a deliberate decision, not an oversight: registration and sign-in are how an
     * account is obtained; verification and reset are reached from a message by somebody who cannot
     * sign in; refresh and logout authenticate with the cookie rather than a bearer token; the
     * invitation pair is reached by somebody who may have no account at all.
     */
    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/logout",
            "POST /api/v1/auth/verify-email",
            "POST /api/v1/auth/verify-email/resend",
            "POST /api/v1/auth/password/forgot",
            "POST /api/v1/auth/password/reset",
            "GET /api/v1/invitations",
            "POST /api/v1/invitations/accept");

    @Autowired
    private MockMvc mockMvc;

    // Actuator contributes a second bean of this type for its own endpoints. This
    // test is about the application's own routes, so it names the MVC one rather
    // than widening the field type or excluding Actuator from the context.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyEndpointOutsideThePublicListRefusesAnAnonymousCaller() throws Exception {
        List<String> reachableWithoutCredentials = mappedRoutes().stream()
                .filter(route -> !PUBLIC_ROUTES.contains(route.describe()))
                .filter(this::respondsToAnonymous)
                .map(Route::describe)
                .sorted()
                .toList();

        assertThat(reachableWithoutCredentials)
                .as("endpoints that answered an anonymous request; add to PUBLIC_ROUTES only on purpose")
                .isEmpty();
    }

    @Test
    void everyRouteOnThePublicListStillExists() {
        // Keeps the list honest in the other direction. A stale entry would quietly
        // stop protecting anything and nobody would notice.
        List<String> mapped = mappedRoutes().stream().map(Route::describe).toList();

        assertThat(mapped).containsAll(PUBLIC_ROUTES);
    }

    @Test
    void theApplicationMapsTheIdentityEndpointsAtAll() {
        // Guards against the matrix passing because nothing was scanned.
        assertThat(mappedRoutes()).hasSizeGreaterThan(15);
    }

    @Test
    void theTaskRoutesAreAmongTheOnesScanned() {
        // Tasks add a whole module of routes, including the first ones nested three
        // levels deep. The sweep protects a route by walking over it, so a route it
        // never sees is a route it never protected.
        List<String> mapped = mappedRoutes().stream().map(Route::describe).toList();

        assertThat(mapped)
                .contains(
                        "POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks",
                        "GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/tasks",
                        "GET /api/v1/workspaces/{workspaceId}/tasks",
                        "GET /api/v1/workspaces/{workspaceId}/tasks/{taskId}",
                        "PATCH /api/v1/workspaces/{workspaceId}/tasks/{taskId}",
                        "DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}",
                        "POST /api/v1/workspaces/{workspaceId}/tasks/{taskId}/status",
                        "PUT /api/v1/workspaces/{workspaceId}/tasks/{taskId}/assignee",
                        "DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}/assignee",
                        "GET /api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks",
                        "POST /api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks",
                        "PATCH /api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks/{subtaskId}",
                        "DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks/{subtaskId}",
                        "POST /api/v1/workspaces/{workspaceId}/tasks/{taskId}/subtasks/{subtaskId}/status",
                        "GET /api/v1/workspaces/{workspaceId}/tasks/{taskId}/dependencies",
                        "POST /api/v1/workspaces/{workspaceId}/tasks/{taskId}/dependencies",
                        "DELETE /api/v1/workspaces/{workspaceId}/tasks/{taskId}/dependencies/{dependsOnTaskId}");
    }

    @Test
    void theWorkspaceTeamAndProjectRoutesAreAmongTheOnesScanned() {
        // The matrix protects a route by walking over it, so a route it never sees
        // is a route it never protected. Naming a few of the newest ones keeps the
        // sweep from passing vacuously for a whole module.
        List<String> mapped = mappedRoutes().stream().map(Route::describe).toList();

        assertThat(mapped)
                .contains(
                        "PATCH /api/v1/workspaces/{workspaceId}",
                        "DELETE /api/v1/workspaces/{workspaceId}",
                        // Needs membership and no permission code, so it is the one
                        // workspace route most worth seeing in this sweep: the only
                        // thing between it and an anonymous caller is the chain.
                        "GET /api/v1/workspaces/{workspaceId}/me",
                        "POST /api/v1/workspaces/{workspaceId}/archive",
                        "POST /api/v1/workspaces/{workspaceId}/unarchive",
                        "POST /api/v1/workspaces/{workspaceId}/teams",
                        "GET /api/v1/workspaces/{workspaceId}/teams",
                        "PATCH /api/v1/workspaces/{workspaceId}/teams/{teamId}",
                        "DELETE /api/v1/workspaces/{workspaceId}/teams/{teamId}",
                        "POST /api/v1/workspaces/{workspaceId}/teams/{teamId}/members",
                        "DELETE /api/v1/workspaces/{workspaceId}/teams/{teamId}/members/{userId}",
                        "PUT /api/v1/workspaces/{workspaceId}/teams/{teamId}/lead",
                        "DELETE /api/v1/workspaces/{workspaceId}/teams/{teamId}/lead",
                        "POST /api/v1/workspaces/{workspaceId}/projects",
                        "GET /api/v1/workspaces/{workspaceId}/projects",
                        "PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}",
                        "DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}",
                        "POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/status",
                        "POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/members",
                        "DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}/members/{userId}",
                        "PUT /api/v1/workspaces/{workspaceId}/projects/{projectId}/owner",
                        "DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}/owner");
    }

    @Test
    void theAdminPanelRoutesAreAmongTheOnesScanned() {
        // These matter more than most. They are the only routes in the platform
        // that cross workspaces, so one left open would expose the whole
        // installation rather than one workspace. A route the sweep never walks is
        // a route it never protected.
        List<String> mapped = mappedRoutes().stream().map(Route::describe).toList();

        assertThat(mapped)
                .contains(
                        "GET /api/v1/admin/statistics",
                        "GET /api/v1/admin/activity",
                        "GET /api/v1/admin/projects",
                        "GET /api/v1/admin/accounts",
                        "PATCH /api/v1/users/{userId}",
                        "POST /api/v1/users/{userId}/unlock",
                        "POST /api/v1/users/{userId}/password-reset",
                        "POST /api/v1/users/{userId}/resend-verification",
                        "PUT /api/v1/users/{userId}/platform-role",
                        "DELETE /api/v1/users/{userId}/platform-role",
                        "PUT /api/v1/workspaces/{workspaceId}/roles/{roleSlug}/permissions");
    }

    private boolean respondsToAnonymous(Route route) {
        try {
            int status = mockMvc.perform(MockMvcRequestBuilders.request(route.method(), route.probeUri()))
                    .andReturn()
                    .getResponse()
                    .getStatus();

            // 401 and 403 both mean the request was stopped before the handler.
            // Anything else means it was let through.
            return status != 401 && status != 403;
        } catch (Exception e) {
            // An exception means it reached the application, which is what this test
            // is looking for.
            return true;
        }
    }

    private List<Route> mappedRoutes() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(entry -> routesOf(entry.getKey(), entry.getValue()))
                .filter(route -> route.pattern().startsWith("/api/"))
                .toList();
    }

    private Stream<Route> routesOf(RequestMappingInfo info, HandlerMethod handler) {
        Set<String> patterns = info.getPathPatternsCondition() == null
                ? Set.of()
                : info.getPathPatternsCondition().getPatternValues();

        Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                info.getMethodsCondition().getMethods();

        return patterns.stream()
                .flatMap(pattern -> methods.stream()
                        .map(method -> new Route(HttpMethod.valueOf(method.name()), pattern)));
    }

    /**
     * One mapped route.
     *
     * @param pattern the declared pattern, which may contain variables
     */
    private record Route(HttpMethod method, String pattern) {

        String describe() {
            return method.name() + " " + pattern;
        }

        /**
         * A concrete address for the pattern.
         *
         * <p>Path variables are filled with a random identifier. What the endpoint would do with it
         * does not matter: an anonymous request must be stopped before the handler is ever reached.
         */
        String probeUri() {
            return pattern.replaceAll("\\{[^/}]+\\}", java.util.UUID.randomUUID().toString());
        }
    }
}
