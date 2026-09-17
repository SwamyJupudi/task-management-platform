package com.company.taskmanagementplatform.users;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.security.AccountStatusProvider;
import com.company.taskmanagementplatform.common.security.SecurityProperties;

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
 * <p>It is also where {@code app.security.require-email-verification} is honoured for an established
 * session. Relaxing only the sign-in check left the demo issuing tokens that this adapter then
 * reported unusable, so every request after a successful sign-in was refused {@code
 * ACCOUNT_INACTIVE}. The two checks have to agree, and this is the second of them.
 */
@Component
class AccountStatusAdapter implements AccountStatusProvider {

    private final UserRepository users;
    private final boolean requireEmailVerification;

    AccountStatusAdapter(UserRepository users, SecurityProperties security) {
        this.users = users;
        this.requireEmailVerification = security.requireEmailVerification();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountState> findAccountState(UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .map(user -> new AccountState(user.getId(), usable(user), user.getPasswordChangedAt()));
    }

    /**
     * Whether a token issued for this account is still honoured.
     *
     * <p>The single place the demo's relaxation is applied, and it is applied on the read rather than
     * on the write. Registration stores the same row under every profile -- it has to, because {@code
     * users_verified_when_active_check} forbids an unconfirmed account from being {@code ACTIVE} --
     * so what changes here is only whether an unconfirmed one is still answered for.
     *
     * <p>The relaxation is exactly one status wide. Deactivated and deleted accounts are refused
     * either way.
     */
    private boolean usable(User user) {
        return requireEmailVerification ? user.isUsable() : user.isUsableWithoutVerification();
    }
}
