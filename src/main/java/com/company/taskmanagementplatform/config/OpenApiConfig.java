package com.company.taskmanagementplatform.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.company.taskmanagementplatform.common.error.ApiErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Describes the API document that springdoc generates from the controllers.
 *
 * <p>The document is generated rather than hand-written, so it cannot drift from the code.
 *
 * <p>The bearer scheme is declared globally and applied as a default requirement, so every operation
 * is documented as protected unless it says otherwise. That is the same way round as the filter
 * chain, where everything is closed unless listed, and for the same reason: the failure mode of
 * forgetting is a wrongly locked door rather than a wrongly open one.
 *
 * <p>The refresh cookie is not described here. It is set and read by the server, is never touched by
 * client code, and documenting it as an input would invite somebody to try to supply one.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /** Matches the name on {@link ApiErrorResponse}, which is what the document is keyed by. */
    private static final String ERROR_SCHEMA = "ApiError";

    /**
     * The statuses every endpoint can answer regardless of what it does.
     *
     * <p>Kept to the ones produced by the filter chain and the exception handler rather than by any
     * particular controller. A status a single endpoint raises belongs on that endpoint.
     */
    private static final Map<String, String> SHARED_ERROR_STATUSES = new LinkedHashMap<>(Map.of(
            "400", "The request could not be read or failed validation",
            "401", "No credentials were supplied, or the access token is invalid or expired",
            "403", "The caller is known but lacks the necessary permission",
            "404", "No such resource, or none the caller is allowed to know about",
            "500", "Unexpected failure; the request id correlates it with the server logs"));

    @Bean
    public OpenAPI taskManagementOpenApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title("Internal Task and Project Management Platform API")
                        .version("v1")
                        .description(
                                """
                                REST API for the internal task and project management platform.

                                Errors share one body shape across every endpoint. See the ApiError schema.
                                Each response carries an X-Request-Id header that correlates it with the server logs.

                                Endpoints require a bearer access token unless documented otherwise. A token that is
                                missing, invalid or expired is answered 401; a valid one without the necessary
                                permission is answered 403. A workspace the caller has no relationship with is
                                answered 404 rather than 403, so that identifiers cannot be probed.
                                """)
                        .contact(new Contact().name("Platform Team"))
                        .license(new License().name("Internal use only")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Short-lived access token from POST /auth/login or /auth/refresh. "
                                                        + "Hold it in memory; the refresh token is a cookie the "
                                                        + "server manages.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Puts the shared error body in the document and points every operation at it.
     *
     * <p>Springdoc writes a schema into the document only when an operation refers to it. The error
     * body is produced by the exception handler rather than returned from a controller method, so no
     * signature mentions it and nothing referred to it: the promise of one error shape was made in the
     * description while the schema itself was absent. Declaring it here is the same decision as the
     * bearer scheme above, made once and centrally rather than repeated as annotations on every
     * method, where it would be forgotten on the next one added.
     *
     * <p>An operation that already documents a status keeps what it says. This only fills gaps.
     */
    @Bean
    public OpenApiCustomizer sharedErrorResponses() {
        return openApi -> {
            Components components = openApi.getComponents();
            ModelConverters.getInstance().readAll(ApiErrorResponse.class).forEach(components::addSchemas);

            MediaType errorBody = new MediaType()
                    .schema(new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + ERROR_SCHEMA));
            Content errorContent = new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, errorBody);

            openApi.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .forEach(operation -> {
                        ApiResponses responses = operation.getResponses();
                        SHARED_ERROR_STATUSES.forEach((status, description) -> {
                            if (responses.get(status) == null) {
                                responses.addApiResponse(
                                        status,
                                        new ApiResponse().description(description).content(errorContent));
                            }
                        });
                    });
        };
    }
}
