package com.company.taskmanagementplatform.config;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built interface from this application, so that both halves share one origin.
 *
 * <p><strong>Demo only.</strong> A production deployment puts an edge proxy in front of two
 * containers and routes by path; this exists because a free Render account gets one web service and
 * the interface has to come from somewhere. {@code @Profile("demo")} is what keeps it out of
 * production, where the static directory is empty anyway.
 *
 * <h2>One origin is a requirement, not a convenience</h2>
 *
 * <p>The refresh cookie is {@code SameSite=Strict} and path-scoped to {@code /api/v1/auth}. A
 * browser will not attach it to a request made from another site, so an interface served from a
 * second hostname would sign somebody in and then drop them on the next reload, with no error
 * anywhere. Serving the bundle from this process is what makes the session survive.
 *
 * <h2>Why a resolver and not a controller</h2>
 *
 * <p>A single-page application owns its own routes, so {@code /projects/abc} has to answer with the
 * document rather than 404. The obvious way to arrange that is a controller that forwards to {@code
 * index.html} — and it would be the wrong one here. {@code SecurityConfig} permits any request that
 * matches no <em>controller method</em>, and {@code ProtectedRouteMatrixIT} walks every mapped
 * endpoint and demands that anything outside an explicit public list refuse an anonymous caller. A
 * forwarding controller would be a new mapped endpoint that has to answer anonymously, so it would
 * need adding to that list — weakening the guarantee that the list is short and reviewed, for a
 * route that serves a static file.
 *
 * <p>A resource resolver adds no mapping at all. The request is handled by the resource handler,
 * matches no controller method, and is permitted exactly as {@code /assets/index.js} already is.
 * Nothing about the security chain changes.
 *
 * <h2>What it will not serve</h2>
 *
 * <p>{@code /api/**} and {@code /actuator/**} are left alone. Both are mapped endpoints, so the
 * dispatcher reaches them before the resource handler and this resolver never sees them; the check
 * below is a second line for the case of an API path with no handler — a typo, or a route removed in
 * a later version. Returning the document there would turn "no such endpoint" into a 200 with HTML
 * in it, which a client parsing JSON cannot make sense of. Those paths keep the shared error
 * envelope, and the 404 stays a 404.
 */
@Configuration
@Profile("demo")
class SpaResourceConfig implements WebMvcConfigurer {

    /** Prefixes that belong to the API and must never be answered with the document. */
    private static final List<String> RESERVED = List.of("api/", "actuator/", "v3/api-docs", "swagger-ui");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /**
     * The file when there is one, the document when there is not.
     *
     * <p>Extends {@link PathResourceResolver} so that the ordinary resolution — and its traversal
     * protection, which is the part worth not reimplementing — runs first and unchanged.
     */
    private static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }

            if (isReserved(resourcePath)) {
                return null;
            }

            // A path with a file extension is an asset that is genuinely missing:
            // a stale bundle reference, say. Answering those with the document
            // would turn a missing script into an HTML parse error in the console
            // rather than the 404 it is.
            if (hasExtension(resourcePath)) {
                return null;
            }

            return super.getResource("index.html", location);
        }

        private static boolean isReserved(String resourcePath) {
            String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            return RESERVED.stream().anyMatch(path::startsWith);
        }

        private static boolean hasExtension(String resourcePath) {
            int lastSlash = resourcePath.lastIndexOf('/');
            String lastSegment = lastSlash < 0 ? resourcePath : resourcePath.substring(lastSlash + 1);
            return lastSegment.contains(".");
        }
    }
}
