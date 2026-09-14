package com.company.taskmanagementplatform.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;

/**
 * Rate limiting against a real Redis: the limits, the keys, the expiry, the behaviour under concurrency,
 * and what happens when the store is not there.
 *
 * <p>The limits are overridden to figures small enough to reach in a test. The rest of the suite runs
 * with these at their shipped values and no Redis at all, which is how the fail-open path gets exercised
 * everywhere rather than only here.
 *
 * <p><strong>Redis is flushed between tests.</strong> A fixed window survives the test that filled it, so
 * without this the second test to touch a rule would start with the first one's counter and fail for a
 * reason that has nothing to do with what it is asserting.
 */
@Testcontainers(disabledWithoutDocker = true)
class RateLimitIT extends AbstractIntegrationTest {

    /**
     * Started from a static initialiser rather than through {@code @Container}, for the reason {@code
     * AbstractIntegrationTest} gives about the PostgreSQL one: the JUnit extension would stop it after
     * this class, while Spring keeps the context — and its connection pool — cached for the next.
     */
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisAndTightLimits(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Small enough to reach in a test, and still in the same relative order as
        // the shipped figures: the per-account login limit stays above the account
        // lockout's five attempts, because a test suite that inverted that would
        // stop testing the behaviour the application actually has.
        // Each figure is set so that the test about a given rule reaches that rule
        // and not another one first. The auth ceiling in particular has to clear the
        // five sign-in attempts the lockout test needs, or the lockout test would be
        // measuring the address limit instead.
        registry.add("app.rate-limit.requests-per-minute", () -> 40);
        registry.add("app.rate-limit.auth-requests-per-minute", () -> 12);
        registry.add("app.rate-limit.registrations-per-hour", () -> 5);
        registry.add("app.rate-limit.login-attempts-per-account", () -> 6);
        registry.add("app.rate-limit.registrations-per-account", () -> 2);
        registry.add("app.rate-limit.recovery-requests-per-account", () -> 2);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter limiter;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private AccountRateLimitGuard guard;

    @Autowired
    private IdentityFixtures fixtures;

    @BeforeEach
    void clearEveryCounter() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // --- the limiter itself ------------------------------------------------

    @Test
    void allowsUpToTheLimitAndRefusesAfterIt() {
        RateLimitRule rule = new RateLimitRule("unit", 3, Duration.ofMinutes(1));
        String key = key();

        assertThat(limiter.tryConsume(key, rule).allowed()).isTrue();
        assertThat(limiter.tryConsume(key, rule).allowed()).isTrue();
        assertThat(limiter.tryConsume(key, rule).allowed()).isTrue();
        assertThat(limiter.tryConsume(key, rule).allowed()).isFalse();
    }

    @Test
    void countsEachKeySeparately() {
        RateLimitRule rule = new RateLimitRule("unit", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume(key(), rule).allowed()).isTrue();
        // A different subject has its own allowance, which is the whole point of
        // keying by address or account rather than counting globally.
        assertThat(limiter.tryConsume(key(), rule).allowed()).isTrue();
    }

    @Test
    void countsEachRuleSeparatelyForTheSameSubject() {
        String subject = UUID.randomUUID().toString();
        RateLimitRule first = new RateLimitRule("one", 1, Duration.ofMinutes(1));
        RateLimitRule second = new RateLimitRule("two", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume(RateLimitKeys.forAddress(first.name(), subject), first).allowed())
                .isTrue();
        assertThat(limiter.tryConsume(RateLimitKeys.forAddress(second.name(), subject), second).allowed())
                .isTrue();
    }

    @Test
    void theAllowanceComesBackWhenTheWindowExpires() throws Exception {
        // A short window, so the test proves the expiry rather than waiting a minute
        // for it. The window is set by the limiter itself, not by the test, so what
        // is being checked is that PEXPIRE really was applied to the key.
        RateLimitRule rule = new RateLimitRule("expiring", 1, Duration.ofMillis(700));
        String key = key();

        assertThat(limiter.tryConsume(key, rule).allowed()).isTrue();
        assertThat(limiter.tryConsume(key, rule).allowed()).isFalse();

        Thread.sleep(900);

        assertThat(limiter.tryConsume(key, rule).allowed()).isTrue();
    }

    @Test
    void theWindowIsFixedFromTheFirstRequestAndNotExtendedByLaterOnes() throws Exception {
        // If PEXPIRE ran on every increment, a steady stream of refused requests
        // would hold the window open indefinitely and a caller could never recover.
        RateLimitRule rule = new RateLimitRule("fixed-window", 1, Duration.ofMillis(900));
        String key = key();

        assertThat(limiter.tryConsume(key, rule).allowed()).isTrue();
        for (int i = 0; i < 6; i++) {
            Thread.sleep(120);
            limiter.tryConsume(key, rule);
        }
        Thread.sleep(300);

        assertThat(limiter.tryConsume(key, rule).allowed()).isTrue();
    }

    @Test
    void reportsAWaitThatIsNeverLongerThanTheWindow() {
        RateLimitRule rule = new RateLimitRule("wait", 1, Duration.ofMinutes(15));
        String key = key();

        limiter.tryConsume(key, rule);
        RateLimitDecision refused = limiter.tryConsume(key, rule);

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfter()).isLessThanOrEqualTo(Duration.ofMinutes(15));
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 900L);
    }

    @Test
    void allowsExactlyTheLimitWhenEveryRequestArrivesAtOnce() throws Exception {
        // The assertion that justifies the Lua script. Increment-then-expire as two
        // commands would let concurrent callers both see a count of one, and a limit
        // of five would let more than five through.
        RateLimitRule rule = new RateLimitRule("concurrent", 5, Duration.ofMinutes(1));
        String key = key();

        int threads = 32;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (limiter.tryConsume(key, rule).allowed()) {
                        allowed.incrementAndGet();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed.get()).isEqualTo(5);
    }

    @Test
    void allowsEverythingWhenRedisCannotBeReached() {
        // The fail-open decision, against a port with nothing behind it rather than
        // against a mock. Port 1 is privileged and unused, so the connection is
        // refused rather than timing out.
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 1);
        dead.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(dead);
            template.afterPropertiesSet();
            RateLimiter failing = new RedisRateLimiter(template, Duration.ofSeconds(30));

            RateLimitRule rule = new RateLimitRule("unreachable", 1, Duration.ofMinutes(1));
            for (int i = 0; i < 5; i++) {
                assertThat(failing.tryConsume(key(), rule).allowed())
                        .as("an unreachable store must not refuse a request")
                        .isTrue();
            }
        } finally {
            dead.destroy();
        }
    }

    // --- the account guard -------------------------------------------------

    @Test
    void theAccountGuardRefusesOnceTheAccountLimitIsUsedUp() {
        String email = uniqueEmail("guard");

        // Two registrations per address in this context.
        guard.checkRegistration(email);
        guard.checkRegistration(email);

        assertThatRefused(() -> guard.checkRegistration(email));
    }

    @Test
    void theAccountGuardTreatsCapitalisationAsTheSameAccount() {
        String email = uniqueEmail("case");

        guard.checkRecovery(email.toUpperCase(java.util.Locale.ROOT));
        guard.checkRecovery(email);

        assertThatRefused(() -> guard.checkRecovery(email.toLowerCase(java.util.Locale.ROOT)));
    }

    @Test
    void theAccountGuardKeepsItsRulesApart() {
        String email = uniqueEmail("apart");

        guard.checkRegistration(email);
        guard.checkRegistration(email);

        // The registration allowance is spent; recovery has its own.
        guard.checkRecovery(email);
    }

    @Test
    void theAccountGuardNeverWritesTheAddressIntoRedis() {
        String email = uniqueEmail("private");
        guard.checkLogin(email);

        java.util.Set<String> keys = redis.keys("rl:v1:*");

        assertThat(keys).isNotEmpty();
        assertThat(keys).allSatisfy(key -> assertThat(key).doesNotContain("@").doesNotContain("private"));
    }

    // --- over HTTP ---------------------------------------------------------

    @Test
    void refusesWithTheStandardErrorBodyAndARetryAfterHeader() throws Exception {
        // Twelve auth requests per minute per address in this context. Each names a
        // different address, so it is the by-address rule being reached and not the
        // per-account one.
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(loginAttempt(uniqueEmail("http")));
        }

        mockMvc.perform(loginAttempt(uniqueEmail("http")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void theRetryAfterHeaderIsAPositiveNumberOfSeconds() throws Exception {
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(loginAttempt(uniqueEmail("retry")));
        }

        String retryAfter = mockMvc.perform(loginAttempt(uniqueEmail("retry")))
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getHeader("Retry-After");

        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    @Test
    void refusesRegistrationFromOneAddressAfterItsHourlyAllowance() throws Exception {
        // Five per address per hour here. This is the by-address half of the
        // condition architecture.md attached to registration answering 409 for an
        // address that already exists.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(registration(uniqueEmail("reg"))).andExpect(status().isCreated());
        }

        mockMvc.perform(registration(uniqueEmail("reg")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void refusesARepeatedRegistrationForOneAddressBeforeSayingWhetherItExists() throws Exception {
        // The by-account half, and the enumeration defence. The limit is consumed
        // before the existence check, so an attacker gets two answers about an
        // address per hour rather than as many as they care to ask for.
        String email = uniqueEmail("enumerate");

        mockMvc.perform(registration(email)).andExpect(status().isCreated());
        // The second names the same address and is answered honestly: 409.
        mockMvc.perform(registration(email)).andExpect(status().isConflict());
        // The third is refused before the address is looked at, so it cannot be used
        // to confirm anything.
        mockMvc.perform(registration(email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void thePerAccountLoginLimitDoesNotPreemptTheAttemptsTheLockoutIsConfiguredToAllow() throws Exception {
        // The ordering guarantee this feature actually has to provide, and the reason
        // the per-account limit is set ABOVE the lockout threshold rather than at it.
        //
        // The lockout is configured for five consecutive failures. Every one of those
        // five has to be answered by the credential check, because a 429 in the
        // middle of them would replace "the email address or password is incorrect"
        // with "come back later" for somebody who is simply mistyping their password.
        // With the limit at six, the fifth attempt is still the credential check's to
        // answer, and so is the sixth.
        //
        // NOTE ON WHAT IS *NOT* ASSERTED HERE. This deliberately does not go on to
        // assert a 403 ACCOUNT_LOCKED, and the reason is a defect that predates this
        // phase rather than anything about rate limiting: AuthenticationService.login
        // is transactional and throws UnauthorizedException for a wrong password, so
        // the failure counter that User.recordFailedLogin just incremented is rolled
        // back with the transaction and the lock is never reached. It is the same trap
        // RefreshTokenReuseGuard was created to escape, and its javadoc describes it
        // exactly — "the revocation has to outlive the refusal that follows it". The
        // fix belongs with the lockout, not here, and writing this test as though the
        // lock engaged would have hidden it.
        String email = uniqueEmail("lockout");
        fixtures.verifiedUser(email);

        int lockoutThreshold = 5;
        for (int attempt = 0; attempt < lockoutThreshold; attempt++) {
            mockMvc.perform(login(email, "wrong-password-entirely"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        // The sixth is within the per-account limit too, so the credential check is
        // still the thing answering.
        mockMvc.perform(login(email, "wrong-password-entirely"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void aCorrectPasswordStillSignsInWhileTheAccountHasAllowanceLeft() throws Exception {
        // Rate limiting must not become a way of failing a legitimate sign-in. Four
        // wrong attempts and then the right one, all inside the per-account limit.
        String email = uniqueEmail("still-works");
        fixtures.verifiedUser(email);

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(login(email, "wrong-password-entirely")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(email, IdentityFixtures.PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void thePerAccountLoginLimitTakesOverOnceTheLockoutHasHadItsSay() throws Exception {
        // The other side of the same boundary. Once the attempts the lockout is
        // configured for have been spent, continuing to hammer one account is
        // answered by the limit, from any address — which is the case the lockout
        // cannot cover on its own.
        String email = uniqueEmail("beyond-lockout");
        fixtures.verifiedUser(email);

        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(login(email, "wrong-password-entirely"));
        }

        mockMvc.perform(login(email, "wrong-password-entirely"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aRefusedAccountLimitSaysNothingAboutWhetherTheAccountExists() throws Exception {
        // One message for every limit. A message that distinguished "too many
        // attempts against this account" from "too many requests from here" would
        // confirm an address is registered to anybody willing to trip the limit.
        String registered = uniqueEmail("real");
        fixtures.verifiedUser(registered);
        String unregistered = uniqueEmail("not-real");

        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(login(registered, "wrong-password-entirely"));
        }
        String forRegistered = mockMvc.perform(login(registered, "wrong-password-entirely"))
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(login(unregistered, "wrong-password-entirely"));
        }
        String forUnregistered = mockMvc.perform(login(unregistered, "wrong-password-entirely"))
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(messageOf(forRegistered)).isEqualTo(messageOf(forUnregistered));
    }

    @Test
    void aRefusedRequestNeverReachesTheEndpointBehindIt() throws Exception {
        // A limit applied after authentication would be a limit on the replies. This
        // one is refused before the security chain, so nothing downstream runs.
        // Unauthenticated and refused by the security chain, which is what this
        // endpoint does before any limit is involved.
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        // The global ceiling is forty a minute here. Past it the same endpoint is
        // refused by the filter instead, before the security chain runs at all, so
        // no token is parsed and no account status is read.
        for (int i = 0; i < 45; i++) {
            mockMvc.perform(get("/api/v1/auth/me"));
        }

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aRefusalCarriesTheCorsHeadersSoTheBrowserWillHandItToTheCaller() throws Exception {
        // The regression test for a mistake made while building this. The filter first
        // sat in front of the whole security chain, which is where CORS headers are
        // written, so a 429 arrived without Access-Control-Allow-Origin and a browser
        // refused to hand it to the single-page application — the caller saw an opaque
        // network failure instead of the status and the error body. A 429 nobody can
        // read is not a 429.
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(loginAttempt(uniqueEmail("cors")).header("Origin", "http://localhost:5173"));
        }

        mockMvc.perform(loginAttempt(uniqueEmail("cors")).header("Origin", "http://localhost:5173"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aRefusalStillCarriesTheSecurityHeaders() throws Exception {
        // HeaderWriterFilter runs before CorsFilter, and the limiter sits after both, so
        // a refusal is as hardened as any other response rather than a bare body written
        // from outside the chain.
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(loginAttempt(uniqueEmail("headers")));
        }

        mockMvc.perform(loginAttempt(uniqueEmail("headers")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void aRefusalCarriesARequestIdSoItCanBeFoundInTheLogs() throws Exception {
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(loginAttempt(uniqueEmail("requestid")));
        }

        mockMvc.perform(loginAttempt(uniqueEmail("requestid")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void eachRequestConsumesExactlyOneCountRatherThanTwo() throws Exception {
        // Boot registers every Filter bean in the servlet chain automatically, so
        // without the disabled registration this filter would run twice per request:
        // once in front of the chain and once inside it. Every limit would be silently
        // halved. Twelve requests must therefore still be allowed, not six.
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(loginAttempt(uniqueEmail("once")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginAttempt(uniqueEmail("once"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void aPreflightIsNeverRateLimited() throws Exception {
        // Refusing an OPTIONS does not refuse the request behind it; it stops the
        // browser being able to ask, which reads as the application being broken.
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .options("/api/v1/auth/login")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void healthProbesAreNeverRateLimited() throws Exception {
        // An orchestrator polls these every few seconds. A limit that made a
        // liveness probe fail would restart a healthy instance.
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    private static String messageOf(String body) {
        int start = body.indexOf("\"message\":\"") + "\"message\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private static String key() {
        return RateLimitKeys.forAddress("unit", UUID.randomUUID().toString());
    }

    private static void assertThatRefused(Runnable action) {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(action::run))
                .isInstanceOf(com.company.taskmanagementplatform.common.error.TooManyRequestsException.class);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginAttempt(String email) {
        return login(email, "whatever-the-password-is");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registration(String email) {
        return post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\","
                        + "\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}");
    }

}
