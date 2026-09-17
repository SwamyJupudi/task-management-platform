package com.company.taskmanagementplatform.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.taskmanagementplatform.common.security.AccountStatusProvider;
import com.company.taskmanagementplatform.support.TestSecurityProperties;

/**
 * Whether a token issued for an account is still honoured, which is the one question the
 * authentication filter asks on every authenticated request.
 *
 * <p>This is where {@code app.security.require-email-verification} is honoured for an established
 * session, and the tests exist because getting only half of it right shipped a demo that signed
 * people in and then refused every request they made: the sign-in check was relaxed and this one was
 * not, so the filter answered {@code ACCOUNT_INACTIVE} to a token it had just caused to be issued.
 */
@ExtendWith(MockitoExtension.class)
class AccountStatusAdapterTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Mock
    private UserRepository users;

    @Test
    void refusesAnUnverifiedAccountWhereVerificationIsRequired() {
        // Production. A token for an unconfirmed address is not honoured.
        assertThat(state(pending(), true).usable()).isFalse();
    }

    @Test
    void answersForAnUnverifiedAccountWhereVerificationIsNotRequired() {
        // The demo profile, and the half that was missing.
        assertThat(state(pending(), false).usable()).isTrue();
    }

    @Test
    void refusesADeactivatedAccountUnderEitherSetting() {
        // The relaxation is exactly one status wide.
        User deactivated = pending();
        deactivated.markEmailVerified(NOW);
        deactivated.deactivate();

        assertThat(state(deactivated, true).usable()).isFalse();
        assertThat(state(deactivated, false).usable()).isFalse();
    }

    @Test
    void answersForAVerifiedAccountUnderEitherSetting() {
        User verified = pending();
        verified.markEmailVerified(NOW);

        assertThat(state(verified, true).usable()).isTrue();
        assertThat(state(verified, false).usable()).isTrue();
    }

    private static User pending() {
        return User.register("ada@example.com", "hash", "Ada", "Lovelace", NOW);
    }

    private AccountStatusProvider.AccountState state(User user, boolean requireEmailVerification) {
        when(users.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));

        AccountStatusAdapter adapter = new AccountStatusAdapter(
                users,
                requireEmailVerification
                        ? TestSecurityProperties.defaults()
                        : TestSecurityProperties.withoutEmailVerification());

        return adapter.findAccountState(UUID.randomUUID()).orElseThrow();
    }
}
