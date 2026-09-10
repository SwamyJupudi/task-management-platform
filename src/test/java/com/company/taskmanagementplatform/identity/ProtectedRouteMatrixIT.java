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
