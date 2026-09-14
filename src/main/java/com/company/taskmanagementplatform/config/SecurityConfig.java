package com.company.taskmanagementplatform.config;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;

import com.company.taskmanagementplatform.common.ratelimit.RateLimitFilter;
import com.company.taskmanagementplatform.common.security.AccessTokenService;
import com.company.taskmanagementplatform.common.security.AccountStatusProvider;
import com.company.taskmanagementplatform.common.security.JwtAuthenticationFilter;
import com.company.taskmanagementplatform.common.security.RestAccessDeniedHandler;
import com.company.taskmanagementplatform.common.security.RestAuthenticationEntryPoint;
import com.company.taskmanagementplatform.common.security.SecurityErrorWriter;

/**
 * The security chain.
 *
 * <p>Everything is closed unless it appears in the list below, which is the way round that fails
 * safely: forgetting to protect a new endpoint leaves it protected, and a mistake shows up as a
 * refused request rather than as an open door nobody notices. {@code ProtectedRouteMatrixIT} walks
 * every mapped handler and asserts the same thing from the outside.
 *
 * <p>Cross-site request forgery protection stays off. The API is authenticated by a bearer token,
 * which a browser does not attach on its own. The refresh cookie is the one exception, and it is
 * defended differently: {@code SameSite=Strict} keeps it off cross-site requests entirely, and it is
 * path-scoped so it is not even sent with ordinary calls. If the transport ever moves off the cookie,
 * or the frontend is served from another site, this decision has to be revisited.
 *
 * <p>The stateless session policy and the CORS source are unchanged from the foundation phase. The
 * headers gained four in the hardening phase — a content security policy, a permissions policy and
 * the two cross-origin isolation headers — written by {@link ResponseSecurityHeaders}. The four the
 * foundation phase set are untouched.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private final String basePath;

    public SecurityConfig(@Value("${app.api.base-path}") String basePath) {
        this.basePath = basePath;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            AccessTokenService accessTokenService,
            AccountStatusProvider accountStatusProvider,
            SecurityErrorWriter errorWriter) {
        return new JwtAuthenticationFilter(accessTokenService, accountStatusProvider, errorWriter);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            // Named explicitly because more than one bean implements this type: the
            // one declared below, and Spring's own mvcHandlerMappingIntrospector.
            // Resolving by type alone is ambiguous, and resolving by parameter name
            // would leave the chain's CORS policy hostage to a rename.
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource,
            ObjectProvider<HandlerMapping> handlerMappings,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        return http.cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                        .httpStrictTransportSecurity(hsts ->
                                hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // The four the framework does not write. One writer rather
                        // than four registrations, because two content security
                        // policies on one response are intersected rather than
                        // chosen between; ResponseSecurityHeaders says why at length.
                        .addHeaderWriter(new ResponseSecurityHeaders()))
                // Both produce the shared error body. Without them a failure inside
                // the filter chain would return a container error page, because the
                // @RestControllerAdvice never sees it.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(requests -> requests
                        // Reachable by somebody who has no account, or cannot yet sign in.
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/verify-email/resend").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/password/forgot").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/auth/password/reset").permitAll()
                        // An invitation reaches somebody who may have no account at
                        // all. Acceptance still requires signing in when the invited
                        // address already has one; the service enforces that.
                        .requestMatchers(HttpMethod.GET, basePath + "/invitations").permitAll()
                        .requestMatchers(HttpMethod.POST, basePath + "/invitations/accept").permitAll()
                        // Operational and documentation surfaces. Actuator exposes
                        // only health, and only without detail, in production.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // An address that maps to no handler at all is answered by the
                        // dispatcher, which raises NoResourceFoundException and reaches
                        // the @RestControllerAdvice as a 404 in the standard body. Without
                        // this the catch-all below would answer 401 instead, which claims
                        // the address exists and is merely protected. Nothing is exposed:
                        // a request permitted here has no handler to reach.
                        .requestMatchers(unmappedRequest(handlerMappings)).permitAll()
                        .anyRequest().authenticated())
                // Rate limiting before authentication, and immediately after CORS.
                //
                // Before authentication because verifying a token, reading an account's
                // status and comparing a bcrypt hash are all work an unauthenticated
                // caller can make this application do; a limit applied afterwards would
                // bound the replies rather than the work.
                //
                // After CORS because Spring Security writes the CORS headers from inside
                // this chain. A 429 produced in front of the chain would carry none of
                // them, and a browser would refuse to hand the response to the
                // single-page application at all — which would make the error envelope
                // and the Retry-After header unreadable by the only client that needs
                // them. RateLimitFilter records this at more length.
                .addFilterAfter(rateLimitFilter, CorsFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Matches a request that no controller or actuator endpoint claims.
     *
     * <p>Only handler-method mappings are consulted. The mapping that serves static files answers to
     * every address, so asking it would make no address look unmapped; asking the endpoint mappings
     * asks the question that matters, which is whether this address is an endpoint at all.
     *
     * <p>The rule is deliberately one-sided. A handler that claims the address but rejects the
     * request, by method or by content type, counts as mapped, as does any failure while asking. So
     * does anything unexpected. Being unsure sends the request to the authenticated catch-all below,
     * which means a mistake here refuses a caller rather than admitting one.
     *
     * <p>Mappings are resolved once, on first use rather than at configuration time, because they
     * are built from the same web infrastructure that this chain is part of.
     */
    private static RequestMatcher unmappedRequest(ObjectProvider<HandlerMapping> handlerMappings) {
        Supplier<List<HandlerMapping>> endpointMappings = SingletonSupplier.of(() -> handlerMappings.stream()
                .filter(AbstractHandlerMethodMapping.class::isInstance)
                .toList());

        return request -> {
            HttpServletRequest probe = new IsolatedAttributesRequest(request);
            for (HandlerMapping mapping : endpointMappings.get()) {
                try {
                    if (mapping.getHandler(probe) != null) {
                        return false;
                    }
                } catch (Exception ex) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * A request whose attributes can be written without the real request seeing it.
     *
     * <p>Asking a handler mapping whether it matches makes it record what it matched. That happens
     * here while the request is still in the filter chain, long before the dispatcher runs the same
     * lookup for real, so the writes are kept local rather than left behind for it to find.
     */
    private static final class IsolatedAttributesRequest extends HttpServletRequestWrapper {

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private IsolatedAttributesRequest(HttpServletRequest request) {
            super(request);
            for (String name : Collections.list(request.getAttributeNames())) {
                attributes.put(name, request.getAttribute(name));
            }
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(new LinkedHashSet<>(attributes.keySet()));
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(orEmpty(properties.allowedOrigins()));
        configuration.setAllowedMethods(orEmpty(properties.allowedMethods()));
        configuration.setAllowedHeaders(orEmpty(properties.allowedHeaders()));
        configuration.setExposedHeaders(orEmpty(properties.exposedHeaders()));
        configuration.setAllowCredentials(properties.allowCredentials());
        configuration.setMaxAge(properties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}
