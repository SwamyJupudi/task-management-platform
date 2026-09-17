package com.company.taskmanagementplatform.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * What the access line says, and the two things it must never say: a query string, and nothing at
 * all.
 */
class RequestCompletionLogFilterTest {

    private final RequestCompletionLogFilter filter = new RequestCompletionLogFilter();
    private final CapturingAppender appender = new CapturingAppender();
    private Logger logger;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(RequestCompletionLogFilter.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    @Test
    void logsTheMethodPathStatusAndDurationOfAFinishedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(appender.messages()).hasSize(1);
        assertThat(appender.messages().get(0))
                .contains("method=POST")
                .contains("path=/api/v1/tasks")
                .contains("status=201")
                .contains("durationMs=");
    }

    @Test
    void logsARefusedRequestToo() throws Exception {
        // The filter sits outside the security chain, so a request the chain refuses
        // still produces a line. Without that, exactly the requests worth noticing
        // would be the ones that left no trace.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(appender.messages().get(0)).contains("status=401");
    }

    @Test
    void neverWritesTheQueryStringBecauseAnInvitationTokenTravelsInOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        request.setQueryString("token=a-live-single-use-credential");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(appender.messages().get(0))
                .contains("path=/api/v1/workspaces")
                .doesNotContain("token")
                .doesNotContain("a-live-single-use-credential");
    }

    @Test
    void logsEvenWhenTheChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        FilterChain exploding = (req, res) -> {
            throw new IllegalStateException("failed inside the chain");
        };

        try {
            filter.doFilter(request, response, exploding);
        } catch (IllegalStateException expected) {
            // The filter must not swallow it; it only has to log the outcome.
        }

        assertThat(appender.messages()).hasSize(1);
        assertThat(appender.messages().get(0)).contains("status=500");
    }

    @Test
    void healthProbesAreNotLoggedAtInfo() throws Exception {
        // An orchestrator polls this every few seconds. At INFO those lines would
        // crowd out everything worth reading.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/liveness");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(appender.messages()).isEmpty();
    }

    @Test
    void healthProbesAreStillRecoverableAtDebug() throws Exception {
        logger.setLevel(Level.DEBUG);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(appender.messages()).hasSize(1);
        assertThat(appender.messages().get(0)).contains("path=/actuator/health");
    }

    @Test
    void leavesNothingBehindInTheLoggingContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        // A pooled thread outlives the request. A key left behind would be
        // attributed to whoever the thread serves next.
        assertThat(org.slf4j.MDC.get("http.request.method")).isNull();
        assertThat(org.slf4j.MDC.get("url.path")).isNull();
        assertThat(org.slf4j.MDC.get("http.response.status_code")).isNull();
        assertThat(org.slf4j.MDC.get("event.duration")).isNull();
    }

    @Test
    void carriesTheEcsFieldsOnTheEventItself() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/tasks/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, mock(FilterChain.class));

        // Under the prod profile the console format is ecs, which turns these into
        // queryable fields rather than text inside a message.
        assertThat(appender.events().get(0).getMDCPropertyMap())
                .containsEntry("http.request.method", "PATCH")
                .containsEntry("url.path", "/api/v1/tasks/1")
                .containsEntry("http.response.status_code", "200")
                .containsKey("event.duration");
    }

    @Test
    void doesNotLogARequestThatHasBeenHandedToAnotherThread() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        request.setAsyncSupported(true);
        request.startAsync();

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        // It has not finished, and its status is not yet the one the client sees.
        assertThat(appender.messages()).isEmpty();
    }

    private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

        private final List<ILoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event);
        }

        List<ILoggingEvent> events() {
            return events;
        }

        List<String> messages() {
            return events.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }
    }
}
