package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;

import tools.jackson.databind.json.JsonMapper;

/**
 * Seeing and ending your own sessions, which is the visible half of token management.
 *
 * <p>Every assertion here is also about scope: a session list is a record of where and when somebody
 * signs in, so it is shown to its owner and to nobody else, administrator included.
 */
class SessionManagementIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Test
    void listsTheSessionsOfTheCallerAndMarksExactlyOneAsCurrent() throws Exception {
        String email = uniqueEmail("sessions");
        UserAccount person = fixtures.verifiedUser(email);
        Cookie first = signIn(email);
        signIn(email);

        String body = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id()))
                        .cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Counted in Java rather than with a JsonPath filter. A filter expression
        // returns an array whatever it matches, so asserting that it exists passes
        // even when nothing is marked current, which is what this test previously
        // did and proved nothing.
        var sessions = json.readTree(body);
        long current = 0;
        for (var session : sessions) {
            if (session.get("current").asBoolean()) {
                current++;
            }
        }

        assertThat(current).as("sessions marked as the caller's own").isEqualTo(1);
    }

    @Test
    void marksNoSessionAsCurrentWhenNoCookieIsPresented() throws Exception {
        // The other half of the same behaviour, and the reason the assertion above
        // has to be an equality rather than a presence check.
        String email = uniqueEmail("no-cookie");
        UserAccount person = fixtures.verifiedUser(email);
        signIn(email);

        String body = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"current\":false");
    }

    @Test
    void recordsWhereASessionCameFromSoItCanBeRecognised() throws Exception {
        String email = uniqueEmail("recognise");
        UserAccount person = fixtures.verifiedUser(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.USER_AGENT, "IntegrationTestBrowser/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userAgent").value("IntegrationTestBrowser/1.0"))
                .andExpect(jsonPath("$[0].ipAddress").isNotEmpty());
    }

    @Test
    void endingOneSessionLeavesTheOthersAlone() throws Exception {
        String email = uniqueEmail("selective");
        UserAccount person = fixtures.verifiedUser(email);
        Cookie doomed = signIn(email);
        Cookie survivor = signIn(email);

        String body = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id()))
                        .cookie(doomed))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID doomedSession = currentSessionIdIn(body);

        mockMvc.perform(delete("/api/v1/auth/sessions/" + doomedSession)
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(doomed)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(survivor)).andExpect(status().isOk());
    }

    @Test
    void signingOutEverywhereEndsThemAll() throws Exception {
        String email = uniqueEmail("everywhere");
        UserAccount person = fixtures.verifiedUser(email);
        Cookie one = signIn(email);
        Cookie two = signIn(email);

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(person.id())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(one)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(two)).andExpect(status().isUnauthorized());
    }

    @Test
    void cannotEndSomebodyElsesSession() throws Exception {
        // The identifier comes from the security context, never from the path, so
        // there is no shape of request that reaches another person's session.
        String email = uniqueEmail("victim");
        UserAccount victim = fixtures.verifiedUser(email);
        Cookie victimCookie = signIn(email);

        UserAccount attacker = fixtures.verifiedUser(uniqueEmail("attacker"));

        String body = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(victim.id()))
                        .cookie(victimCookie))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID victimSession = currentSessionIdIn(body);

        mockMvc.perform(delete("/api/v1/auth/sessions/" + victimSession)
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(attacker.id())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(victimCookie)).andExpect(status().isOk());
    }

    @Test
    void aSessionListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sessions")).andExpect(status().isUnauthorized());
    }

    private UUID currentSessionIdIn(String body) throws Exception {
        var sessions = json.readTree(body);
        for (var session : sessions) {
            if (session.get("current").asBoolean()) {
                return UUID.fromString(session.get("sessionId").asText());
            }
        }
        throw new IllegalStateException("No session was marked as the current one");
    }

    private Cookie signIn(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String credentials(String email) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "password", IdentityFixtures.PASSWORD));
    }
}
