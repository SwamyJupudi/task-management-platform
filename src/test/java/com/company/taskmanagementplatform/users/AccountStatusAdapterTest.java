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

/**
 * Whether a token issued for an account is still honoured, which is the one question the
 * authentication filter asks on every authenticated request.
 *
 * <p>The interesting case is the account waiting for approval. It must be usable, or somebody who
 * has just registered would sign in successfully and then be refused every request they made — and
 * it must grant nothing, which it does not, because every workspace, project and task is reached
 * through a membership an unapproved account does not have.
 */
@ExtendWith(MockitoExtension.class)
class AccountStatusAdapterTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Mock
    private UserRepository users;

    @Test
    void answersForAnAccountWaitingForApproval() {
        assertThat(state(waiting()).usable()).isTrue();
    }

    @Test
    void answersForAnApprovedAccount() {
        User approved = waiting();
        approved.approve(UUID.randomUUID(), NOW);

        assertThat(state(approved).usable()).isTrue();
    }

    @Test
    void refusesADeactivatedAccount() {
        User deactivated = waiting();
        deactivated.approve(UUID.randomUUID(), NOW);
        deactivated.deactivate();

        assertThat(state(deactivated).usable()).isFalse();
    }

    @Test
    void refusesAnAccountThatNoLongerExists() {
        when(users.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        assertThat(new AccountStatusAdapter(users).findAccountState(UUID.randomUUID()))
                .isEmpty();
    }

    private static User waiting() {
        return User.register("ada@example.com", "hash", "Ada", "Lovelace", NOW);
    }

    private AccountStatusProvider.AccountState state(User user) {
        when(users.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));
        return new AccountStatusAdapter(users).findAccountState(UUID.randomUUID()).orElseThrow();
    }
}
