package com.company.taskmanagementplatform.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.company.taskmanagementplatform.common.security.SecurityErrorWriter;

/**
 * Wires the rate limiter.
 *
 * <p><strong>There is no {@code @EnableCaching} here, and there must not be one.</strong> Redis arrives in
 * this phase for the rate limiter's counters and for nothing else. The standing decision recorded in
 * {@code architecture.md} is that a resolved permission set must not be cached: a membership change, a role
 * change and a deactivation all have to take effect on the next request, and {@code PermissionResolver}
 * carries that rule in its own contract — "there is no caching behind either method". The easiest way to
 * break it by accident would be to switch caching on, leave a {@code CacheManager} in the context, and let
 * somebody add {@code @Cacheable} to a resolver method that looks expensive. Not enabling it means there is
 * no annotation to add and nothing for one to bind to, so the rule is enforced by the absence of the
 * machinery rather than by review.
 *
 * <p>Redis being unreachable is not an outage here, which is why {@code management.health.redis.enabled} is
 * false in {@code application.properties}: a dependency the application deliberately degrades past must not
 * be able to fail a readiness probe and take a healthy instance out of the load balancer.
 *
 * <p>{@link StringRedisTemplate} rather than a template of its own. The values are counters and the keys are
 * built by {@link RateLimitKeys}; a serializer that wrote Java types into them would make the contents
 * unreadable from {@code redis-cli}, which is where somebody debugging a limit will actually look.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    @Bean
    RateLimiter rateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        if (!properties.enabled()) {
            // Still a bean, so nothing has to be conditional on its absence. The
            // filter takes itself out of the chain and the guard returns early, so
            // this is never consulted; it exists so a disabled limiter is a
            // configuration state rather than a different object graph.
            log.warn("Rate limiting is disabled. Nothing bounds how often any endpoint may be called.");
        } else {
            log.info(
                    "Rate limiting is on: global={}/min auth={}/min registration={}/hour "
                            + "loginPerAccount={}/15min registrationPerAccount={}/hour recoveryPerAccount={}/hour",
                    properties.requestsPerMinute(),
                    properties.authRequestsPerMinute(),
                    properties.registrationsPerHour(),
                    properties.loginAttemptsPerAccount(),
                    properties.registrationsPerAccount(),
                    properties.recoveryRequestsPerAccount());
        }

        return new RedisRateLimiter(redis, properties.unavailableBackoff());
    }

    /**
     * The filter, as a bean rather than a {@code @Component}.
     *
     * <p>It is added to the security chain by {@code SecurityConfig}, immediately after {@code CorsFilter},
     * for the reason set out on the filter itself: a 429 produced ahead of that chain would carry no CORS
     * headers and a browser would hide the error body from the caller.
     */
    @Bean
    RateLimitFilter rateLimitFilter(
            RateLimiter limiter,
            RateLimitProperties properties,
            SecurityErrorWriter errorWriter,
            @Value("${app.api.base-path}") String basePath) {
        return new RateLimitFilter(limiter, properties, errorWriter, basePath);
    }

    /**
     * Stops the servlet container registering the filter a second time.
     *
     * <p>Spring Boot registers every {@code Filter} bean in the servlet chain automatically, which would run
     * this one twice per request: once in front of Spring Security, where its refusal would have no CORS
     * headers, and once inside the chain where it belongs. Two counts would also be consumed for every
     * request, silently halving every limit. Disabling the automatic registration leaves the chain placement
     * as the only one.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterServletRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
