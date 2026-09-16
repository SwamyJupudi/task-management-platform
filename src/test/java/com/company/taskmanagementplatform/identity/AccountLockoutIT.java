package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.users.UserAccount;

import tools.jackson.databind.json.JsonMapper;

/**
 * The account lockout, over HTTP, which is the only place it was ever broken.
 *
 * <p><strong>This test exists because the lockout did not work and every unit test said it did.</strong>
 * {@code AuthenticationService.login} is transactional and throws for a wrong password.
 * {@code verifyCredentials} joined that transaction, so the counter it had just incremented was
 * rolled back by the very exception that reported the failure: the column never moved, and no
 * account ever locked in the running application. {@code UserAccountServiceTest} passed throughout,
 * because a test that calls the service directly has no enclosing transaction to roll back.
 *
 * <p>So the assertions here are deliberately made through the API and against the table. Anything
 * that drove the service directly would pass against the broken version, which is what made the
 * defect survive nine phases.
 *
 * <p>Five attempts, because {@code app.security.lockout.max-attempts} is five. The per-account rate
 * limit is ten in fifteen minutes and is deliberately looser, so the lockout still speaks first;
 * these tests stay under both.
 */
class AccountLockoutIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void repeatedFailedSignInsPersistTheCounterAndLockTheAccount() throws Exception {
        String email = uniqueEmail("lockout");
        UserAccount account = fixtures.verifiedUser(email);

        for (int attempt = 1; attempt <= 5; attempt++) {
            signIn(email, "not-the-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.name()));

            // Read from the table after every attempt rather than only at the end.
            // The bug was that this column stayed at zero however many times somebody
            // tried, so the count as it climbs is the evidence.
            assertThat(failedAttempts(account.id()))
                    .as("failed_login_attempts after attempt %d", attempt)
                    .isEqualTo(attempt);
        }

        assertThat(lockedUntil(account.id()))
                .as("the account should be locked once the limit is reached")
                .isNotNull();

        // The right password now, which is the only way to be told the account is
        // locked: a wrong one still answers with the generic failure, so that
        // somebody who does not hold the password learns nothing about the account.
        signIn(email, IdentityFixtures.PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_LOCKED.name()));
    }

    @Test
    void aWrongPasswordIsStillAnsweredGenericallyWhileTheAccountIsLocked() throws Exception {
        // The lock must not become a way of discovering that an address is registered.
        String email = uniqueEmail("locked-generic");
        fixtures.verifiedUser(email);

        for (int attempt = 0; attempt < 5; attempt++) {
            signIn(email, "not-the-password").andExpect(status().isUnauthorized());
        }

        signIn(email, "still-not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.name()));
    }

    @Test
    void aSuccessfulSignInClearsWhatTheFailuresRecorded() throws Exception {
        // The other half of the same boundary: the failures are committed by their own
        // transaction, and a success has to clear what they left behind rather than
        // finding a stale count on the instance it loaded.
        String email = uniqueEmail("cleared");
        UserAccount account = fixtures.verifiedUser(email);

        signIn(email, "not-the-password").andExpect(status().isUnauthorized());
        signIn(email, "not-the-password").andExpect(status().isUnauthorized());
        assertThat(failedAttempts(account.id())).isEqualTo(2);

        signIn(email, IdentityFixtures.PASSWORD).andExpect(status().isOk());

        assertThat(failedAttempts(account.id()))
                .as("a successful sign-in should reset the count")
                .isZero();
        assertThat(lockedUntil(account.id())).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private int failedAttempts(UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private Object lockedUntil(UUID userId) {
        return jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Object.class, userId);
    }
}
