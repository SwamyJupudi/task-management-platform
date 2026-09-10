package com.company.taskmanagementplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.web.RequestIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Holds the filter-chain error body to the shape the {@code @RestControllerAdvice} produces.
 *
 * <p>The writer serialises with the mapper Spring Boot autoconfigures, which since Boot 4 is a
 * Jackson 3 {@link JsonMapper}. The timestamp assertions below are the guard on that: Jackson
 * renders {@link OffsetDateTime} as a numeric array unless date serialisation is configured the way
 * the application configures it, and a body that changed shape would break every client parsing it.
 */
class SecurityErrorWriterTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SecurityErrorWriter writer = new SecurityErrorWriter(jsonMapper);

    @Test
    void writesTheStandardErrorBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.FORBIDDEN);

        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(body.get("message").asString()).isEqualTo(ErrorCode.FORBIDDEN.defaultMessage());
        assertThat(body.get("path").asString()).isEqualTo("/api/v1/workspaces");
        assertThat(body.get("requestId").asString()).isEqualTo("req-1234");
    }

    @Test
    void writesTheTimestampAsAnIsoStringRatherThanANumericArray() throws Exception {
        // Jackson 2 needed a date module registered for this; Jackson 3 reads and
        // writes java.time itself. Either way the wire format is what clients see,
        // so it is asserted rather than assumed.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.FORBIDDEN);

        JsonNode timestamp = jsonMapper.readTree(response.getContentAsString()).get("timestamp");
        assertThat(timestamp.isString()).as("timestamp should serialise as a string").isTrue();
        assertThat(OffsetDateTime.parse(timestamp.asString())).isNotNull();
    }

    @Test
    void omitsTheErrorsArrayWhenThereAreNoFieldViolations() throws Exception {
        // The security path never produces field violations, and NON_EMPTY keeps the
        // key out of the body entirely rather than sending an empty list.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.FORBIDDEN);

        assertThat(jsonMapper.readTree(response.getContentAsString()).has("errors"))
                .isFalse();
    }

    @Test
    void answersAnUnauthorizedRequestWithTheBearerChallenge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.UNAUTHORIZED);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    }

    @Test
    void doesNotSendTheChallengeOnADenialThatIsNotAboutCredentials() throws Exception {
        // A 403 means the caller is known. Offering a challenge would invite them to
        // authenticate again, which would not change the answer.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.FORBIDDEN);

        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    void leavesAnAlreadyCommittedResponseAlone() throws Exception {
        // Something has already started writing. Appending an error body would
        // corrupt whatever is on the wire rather than replace it.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("partial");
        response.flushBuffer();

        writer.write(request, response, ErrorCode.FORBIDDEN);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("partial");
    }

    @Test
    void marksTheRequestIdUnknownWhenTheFilterDidNotSetOne() throws Exception {
        // The writer sits in the filter chain and may run before RequestIdFilter has
        // stored the attribute. The field stays present and non-null either way, so a
        // client parsing the body never has to handle a missing key.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.UNAUTHORIZED);

        JsonNode requestId = jsonMapper.readTree(response.getContentAsString()).get("requestId");
        assertThat(requestId.asString()).isEqualTo("unknown");
    }
}
