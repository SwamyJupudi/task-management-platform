package com.company.taskmanagementplatform.common.ratelimit;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.security.SecurityErrorWriter;

/**
 * Limits what one network address may ask for, before anything tries to work out who it is.
 *
 * <p><strong>It runs before authentication, and that is the point of putting it in a filter at
 * all.</strong> Authentication is not free: a bearer token is parsed and verified, an account's status is
 * read from the database, a password is compared against a bcrypt hash at strength twelve. All of that is
 * work an unauthenticated caller can make the application do, so a limit applied after authentication
 * would be a limit on the replies rather than on the work.
 *
 * <p><strong>It sits inside the security chain rather than in front of it, immediately after {@code
 * CorsFilter}, and that position was arrived at by getting it wrong first.</strong> The obvious placement is
 * an ordinary servlet filter near {@code RequestIdFilter}, ahead of Spring Security entirely — it is
 * earlier, it is simpler, and it is broken. Spring Security writes the CORS response headers from inside its
 * own chain, so a 429 produced before that chain carries no {@code Access-Control-Allow-Origin}. The browser
 * then refuses to hand the response to the caller at all: the single-page application, which is on a
 * different origin, would see an opaque network failure instead of the 429 and the error body this filter
 * carefully produced. The whole point of returning the platform's error envelope with a {@code Retry-After}
 * is that a client can read it.
 *
 * <p>Placed here, three things hold at once. CORS headers are already on the response, because {@code
 * CorsFilter} has run. The security headers are on it too, because {@code HeaderWriterFilter} runs before
 * {@code CorsFilter}. And nothing has yet tried to work out who the caller is, because every authentication
 * filter — including this application's own {@code JwtAuthenticationFilter} — is added later in the chain. A
 * preflight never reaches this filter either, since {@code CorsFilter} answers those itself; the {@code
 * OPTIONS} guard below is kept anyway, because relying on another filter's short-circuit for a security
 * property is the kind of coupling that breaks quietly.
 *
 * <p>{@code RequestCompletionLogFilter} is still outside and ahead of the whole chain, so a refusal is
 * logged with its 429 and its request id like any other response.
 *
 * <p><strong>Only the address is limited here, never the account.</strong> Limiting by account needs the
 * address out of the request body, and reading a body in a filter consumes the stream: every request would
 * then have to be wrapped in a caching wrapper, including the multipart uploads, so that the controller
 * could read it again. That is a large and permanent cost imposed on every request in the application to
 * avoid passing one string to a collaborator. The account limits live in {@link AccountRateLimitGuard}
 * instead, called from the services that have already parsed the address they are about to act on.
 *
 * <p>Two rules apply, and both are counted. A global ceiling keeps one caller from saturating the
 * container's threads; a much tighter one covers the unauthenticated authentication endpoints, which are
 * the ones worth attacking. Registration is tighter still and hourly. The narrowest applicable rule is
 * checked first, so the refusal names the limit that was actually reached.
 *
 * <p><strong>Two things are deliberately not limited.</strong> A CORS preflight, because refusing an
 * {@code OPTIONS} with a 429 does not refuse the request behind it — it breaks the browser's ability to
 * ask, which reads to the user as the application being broken rather than busy, and a preflight costs
 * nothing to serve. And anything outside the API base path: the actuator endpoints, which an orchestrator
 * polls every few seconds and where a limit that failed a liveness probe would restart a healthy instance,
 * along with the documentation console and the static resources.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final SecurityErrorWriter errorWriter;
    private final String basePath;

    RateLimitFilter(
            RateLimiter limiter,
            RateLimitProperties properties,
            SecurityErrorWriter errorWriter,
            @Value("${app.api.base-path}") String basePath) {
        this.limiter = limiter;
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.basePath = basePath;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        // A preflight is the browser asking permission, not the request itself.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        return !path(request).startsWith(basePath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String address = address(request);

        for (RateLimitRule rule : rulesFor(request)) {
            RateLimitDecision decision = limiter.tryConsume(RateLimitKeys.forAddress(rule.name(), address), rule);
            if (!decision.allowed()) {
                refuse(request, response, rule, decision);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * The rules this request is counted against, narrowest first.
     *
     * <p>Narrowest first so that a caller hammering sign-in is refused by the authentication limit rather
     * than eventually by the global one, which makes the log line say something useful. Every applicable
     * rule is counted, not just the first: a request that passes the tight limit still has to count
     * against the ceiling, or the ceiling would only ever be reached by endpoints no other rule covers.
     */
    private List<RateLimitRule> rulesFor(HttpServletRequest request) {
        String path = path(request);
        boolean post = HttpMethod.POST.matches(request.getMethod());

        if (post && path.equals(basePath + "/auth/register")) {
            return List.of(properties.registration(), properties.auth(), properties.global());
        }
        if (post && path.startsWith(basePath + "/auth/")) {
            return List.of(properties.auth(), properties.global());
        }
        return List.of(properties.global());
    }

    /**
     * Answers 429 in the platform's one error body.
     *
     * <p>Through {@link SecurityErrorWriter} because this runs in a filter, which the {@code
     * @RestControllerAdvice} never sees. The alternative would be a bare container error page with a
     * different shape and no request id, which is the gap that writer was built to close.
     *
     * <p>The log line carries the rule and the path. It does not carry the address: a refused request is
     * already correlated by its request id, the access log line beside it has everything else, and
     * writing a caller's address into every log line is a retention decision this platform has not made —
     * {@code refresh_tokens} keeps one in a column with a comment saying it is never written to a log.
     */
    private void refuse(
            HttpServletRequest request, HttpServletResponse response, RateLimitRule rule, RateLimitDecision decision)
            throws IOException {

        log.warn(
                "Rate limit reached: rule={} limit={} window={} method={} path={}",
                rule.name(),
                rule.limit(),
                rule.window(),
                request.getMethod(),
                path(request));

        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        errorWriter.write(request, response, ErrorCode.TOO_MANY_REQUESTS);
    }

    /**
     * The caller's address.
     *
     * <p>From the request rather than from a forwarded header directly, which is the same decision {@code
     * ClientContext} made and for the same reason: behind a proxy that is handled once, centrally, by
     * {@code server.forward-headers-strategy}, and every call site deciding for itself whether to trust a
     * client-settable header is how a limit becomes trivially evadable by sending {@code
     * X-Forwarded-For: anything}.
     */
    private static String address(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
