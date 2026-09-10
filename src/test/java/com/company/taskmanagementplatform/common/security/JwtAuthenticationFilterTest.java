package com.company.taskmanagementplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.support.TestSecurityProperties;

import tools.jackson.databind.json.JsonMapper;

class JwtAuthenticationFilterTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final UUID USER_ID = UUID.randomUUID();

    private final SecurityBeansConfig beans = new SecurityBeansConfig();
    private final SecurityProperties properties = TestSecurityProperties.defaults();

    private AccessTokenService accessTokens;
    private AccountStatusProvider accounts;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        javax.crypto.SecretKey key = beans.jwtSigningKey(properties);
        accessTokens = new AccessTokenService(
                beans.jwtEncoder(key),
                beans.jwtDecoder(key, properties),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        accounts = mock(AccountStatusProvider.class);
        // Jackson 3 reads and writes java.time out of the box, so a plain mapper
        // serialises the timestamp in the error body exactly as the autoconfigured
        // one does. No date module to register and none to forget.
        JsonMapper jsonMapper = JsonMapper.builder().build();
        filter = new JwtAuthenticationFilter(accessTokens, accounts, new SecurityErrorWriter(jsonMapper));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesAnAnonymousRequestStraightThrough() throws Exception {
        // Whether anonymous access is allowed is the filter chain's decision. This
        // filter only turns a token into a caller; conflating the two is how an
        // endpoint ends up accidentally public.
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticatesAValidToken() throws Exception {
        when(accounts.findAccountState(USER_ID))
                .thenReturn(Optional.of(new AccountStatusProvider.AccountState(
                        USER_ID, true, NOW.minus(Duration.ofDays(1)))));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithToken(accessTokens.issue(USER_ID).value()), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void refusesAMalformedToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithToken("not-a-jwt"), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(ErrorCode.TOKEN_INVALID.name());
    }

    @Test
    void producesTheSharedErrorBodyRatherThanAContainerPage() throws Exception {
        // The gap this closes: a @RestControllerAdvice never sees a failure raised
        // inside a filter, so without this the shape would silently differ here.
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithToken("not-a-jwt"), response, mock(FilterChain.class));

        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("timestamp", "status", "code", "message", "path", "requestId");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void refusesATokenWhoseAccountIsGone() throws Exception {
        when(accounts.findAccountState(USER_ID)).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(accessTokens.issue(USER_ID).value()), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void refusesATokenWhoseAccountIsNoLongerUsable() throws Exception {
        // This is what makes a deactivation take effect on the very next request
        // rather than whenever the token happens to expire.
        when(accounts.findAccountState(USER_ID))
                .thenReturn(Optional.of(new AccountStatusProvider.AccountState(USER_ID, false, NOW)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(accessTokens.issue(USER_ID).value()), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(ErrorCode.ACCOUNT_INACTIVE.name());
    }

    @Test
    void refusesATokenIssuedBeforeThePasswordChanged() throws Exception {
        // What ends other sessions when somebody changes or resets a password.
        String token = accessTokens.issue(USER_ID).value();
        when(accounts.findAccountState(USER_ID))
                .thenReturn(Optional.of(new AccountStatusProvider.AccountState(
                        USER_ID, true, NOW.plus(Duration.ofMinutes(1)))));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(token), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(ErrorCode.TOKEN_EXPIRED.name());
    }

    @Test
    void acceptsATokenIssuedInTheSameSecondAsThePasswordChange() throws Exception {
        // A JWT timestamp has one-second resolution. Without truncating the stored
        // moment, the fresh token handed back by a password change would be refused.
        String token = accessTokens.issue(USER_ID).value();
        when(accounts.findAccountState(USER_ID))
                .thenReturn(Optional.of(new AccountStatusProvider.AccountState(
                        USER_ID, true, NOW.plusMillis(400))));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(token), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresAnAuthorizationHeaderThatIsNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }
}
