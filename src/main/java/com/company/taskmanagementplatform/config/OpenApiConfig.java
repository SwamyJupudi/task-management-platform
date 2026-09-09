package com.company.taskmanagementplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Describes the API document that springdoc generates from the controllers.
 *
 * <p>The document is generated rather than hand-written, so it cannot drift from the code. The
 * security scheme is added in the identity phase, once the authentication design is approved.
 */
@Configuration
public class OpenApiConfig {

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
                                """)
                        .contact(new Contact().name("Platform Team"))
                        .license(new License().name("Internal use only")));
    }
}
