package com.company.taskmanagementplatform.users;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.security.AccountStatusProvider;

/**
 * Supplies the authentication filter with just enough to decide whether a token still counts.
 *
 * <p>This is the adapter half of the port declared in {@code common.security}. The filter asks a
 * question about an account without importing anything from this module, and this module answers
 * without exposing its entity.
 *
 * <p>One database read on every authenticated request, deliberately and with no cache in front of
 * it. That is what makes a deactivation or a password change take effect on the next request rather
 * than whenever a cached copy expires.
 *
 * <p>An account awaiting approval is usable, so somebody who has just registered can sign in and be
 * shown that they are waiting. {@link User#isUsable()} says why that grants them nothing.
 */
@Component
class AccountStatusAdapter implements AccountStatusProvider {

    private final UserRepository users;

    AccountStatusAdapter(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountState> findAccountState(UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .map(user -> new AccountState(user.getId(), user.isUsable(), user.getPasswordChangedAt()));
    }
}
