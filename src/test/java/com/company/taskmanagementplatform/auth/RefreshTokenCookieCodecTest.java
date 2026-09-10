package com.company.taskmanagementplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.company.taskmanagementplatform.support.TestSecurityProperties;

/**
 * The transport seam. These assertions are the properties the whole cookie decision rests on, so if
 * one of them is ever relaxed it should be here, deliberately, with a reason.
 */
class RefreshTokenCookieCodecTest {

    private final RefreshTokenCookieCodec codec = new RefreshTokenCookieCodec(TestSecurityProperties.defaults());

    @Test
    void marksTheCookieHttpOnlySoAScriptCannotReadIt() {
        // The reason for choosing a cookie over local storage in the first place.
        assertThat(codec.issueHeader("token-value")).contains("HttpOnly");
    }

    @Test
    void marksTheCookieSecureSoItNeverTravelsInClear() {
        assertThat(codec.issueHeader("token-value")).contains("Secure");
    }

    @Test
    void marksTheCookieStrictSoItIsNotSentCrossSite() {
        // This is what stands in for cross-site request forgery protection on the
        // refresh endpoint. If it is ever weakened, that protection has to return.
        assertThat(codec.issueHeader("token-value")).contains("SameSite=Strict");
    }

    @Test
    void scopesTheCookieToTheAuthenticationPath() {
        // So a long-lived credential is not attached to every ordinary request.
        assertThat(codec.issueHeader("token-value")).contains("Path=/api/v1/auth");
    }

    @Test
    void carriesTheTokenValue() {
        assertThat(codec.issueHeader("token-value")).contains("refresh_token=token-value");
    }

    @Test
    void clearsWithTheSameNameAndPath() {
        // A browser only replaces a cookie when both match. A clearing cookie
        // written at a different path leaves the original one in place.
        String cleared = codec.clearHeader();

        assertThat(cleared).contains("refresh_token=");
        assertThat(cleared).contains("Path=/api/v1/auth");
        assertThat(cleared).contains("Max-Age=0");
    }

    @Test
    void readsTheTokenBackFromARequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "token-value"));

        assertThat(codec.read(request)).contains("token-value");
    }

    @Test
    void findsNothingWhenThereAreNoCookies() {
        assertThat(codec.read(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void ignoresOtherCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session", "irrelevant"), new Cookie("theme", "dark"));

        assertThat(codec.read(request)).isEmpty();
    }

    @Test
    void treatsAnEmptyCookieAsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", ""));

        assertThat(codec.read(request)).isEmpty();
    }
}
