package com.company.taskmanagementplatform.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesARequestIdWhenTheClientSendsNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(RequestIdFilter.currentRequestId(request))
                .isEqualTo(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void keepsAWellFormedIdSuppliedByTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/anything");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "trace-abc.123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("trace-abc.123_XYZ");
    }

    @Test
    void replacesAnIdThatCouldPolluteTheLogs() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/anything");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "bad id\nINJECTED ERROR line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).doesNotContain("INJECTED");
    }

    @Test
    void putsTheIdInTheLoggingContextAndClearsItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        String[] seenInsideChain = new String[1];
        doAnswer(invocation -> {
                    seenInsideChain[0] = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
                    return null;
                })
                .when(chain)
                .doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(seenInsideChain[0]).isNotBlank();
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void reportsAPlaceholderWhenTheFilterDidNotRun() {
        assertThat(RequestIdFilter.currentRequestId(new MockHttpServletRequest())).isEqualTo("unknown");
    }
}
