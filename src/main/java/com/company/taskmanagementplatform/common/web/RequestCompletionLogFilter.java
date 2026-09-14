package com.company.taskmanagementplatform.common.web;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One line per finished request: what was asked, what was answered, and how long it took.
 *
 * <p>Until this existed the logs recorded only the requests that failed, because {@code
 * GlobalExceptionHandler} logs a rejection and nothing logs a success. That makes the ordinary
 * questions unanswerable — whether an endpoint is being called at all, whether it got slower after a
 * deployment, which request a user is describing — and the request id was already being generated for
 * exactly those questions.
 *
 * <p><strong>Ordered immediately after {@link RequestIdFilter}, and that is not cosmetic.</strong>
 * That filter puts the correlation id into the logging context, so running before it would produce
 * the one line in the logs that could not be tied to the request it describes. It runs before Spring
 * Security instead of inside it, so a request refused by the filter chain — a bad token, a rate limit
 * — is logged with the status it actually received rather than not at all.
 *
 * <p><strong>The query string is deliberately absent.</strong> An invitation is accepted through
 * {@code GET /invitations?token=...}, so logging a full request line would write a live single-use
 * credential into the log, where it would outlive the token itself and be readable by anybody who can
 * read logs. The path alone answers every question an access log is for. Nothing else about the
 * request is logged either: no header, no body, no cookie.
 *
 * <p><strong>Health probes are logged at DEBUG rather than INFO.</strong> An orchestrator polls
 * {@code /actuator/health} every few seconds for the life of the process, and at INFO those lines
 * would be the overwhelming majority of the log and would push out the ones worth keeping. The line
 * is still produced, so turning this logger up recovers it.
 *
 * <p>The MDC keys use Elastic Common Schema names, so under the prod profile — where the console
 * format is {@code ecs} — each value lands as a queryable field rather than being buried in the
 * message text. They are removed afterwards: the logging context outlives this filter on a pooled
 * thread, and a key left behind would be attributed to somebody else's request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCompletionLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCompletionLogFilter.class);

    private static final String METHOD_KEY = "http.request.method";
    private static final String PATH_KEY = "url.path";
    private static final String STATUS_KEY = "http.response.status_code";
    /** Nanoseconds, which is what Elastic Common Schema means by a duration. */
    private static final String DURATION_KEY = "event.duration";

    private static final String HEALTH_PATH = "/actuator/health";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // A request handed off to another thread has not finished, and its status
            // is not yet the one the client will see. The container runs this filter
            // again on the dispatch that does finish it.
            if (!request.isAsyncStarted()) {
                logCompletion(request, response, System.nanoTime() - startedAt);
            }
        }
    }

    private void logCompletion(HttpServletRequest request, HttpServletResponse response, long elapsedNanos) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

        boolean probe = path != null && path.startsWith(HEALTH_PATH);
        if (probe && !log.isDebugEnabled()) {
            return;
        }

        MDC.put(METHOD_KEY, method);
        MDC.put(PATH_KEY, path);
        MDC.put(STATUS_KEY, Integer.toString(status));
        MDC.put(DURATION_KEY, Long.toString(elapsedNanos));
        try {
            if (probe) {
                log.debug(
                        "Request complete: method={} path={} status={} durationMs={}",
                        method,
                        path,
                        status,
                        durationMillis);
            } else {
                log.info(
                        "Request complete: method={} path={} status={} durationMs={}",
                        method,
                        path,
                        status,
                        durationMillis);
            }
        } finally {
            MDC.remove(METHOD_KEY);
            MDC.remove(PATH_KEY);
            MDC.remove(STATUS_KEY);
            MDC.remove(DURATION_KEY);
        }
    }
}
