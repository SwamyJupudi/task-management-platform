package com.company.taskmanagementplatform.common.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tells the authentication filter whether an account may still be used.
 *
 * <p>Declared here and implemented by the {@code users} module, so that this package depends on no
 * module and every module may depend on it. Without the inversion the filter would import an entity
 * and the boundary would be gone.
 */
public interface AccountStatusProvider {

    Optional<AccountState> findAccountState(UUID userId);

    /**
     * The minimum the filter needs to decide.
     *
     * @param userId who the account belongs to
     * @param usable false when the account is deactivated, unverified or removed, in which case no
     *     token issued for it is honoured
     * @param passwordChangedAt when the password last changed; a token issued before that moment is
     *     refused, which is what makes a password change end other sessions immediately
     */
    record AccountState(UUID userId, boolean usable, Instant passwordChangedAt) {}
}
