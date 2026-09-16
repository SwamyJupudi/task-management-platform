package com.company.taskmanagementplatform.users;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts a failed sign-in in a transaction of its own, so that the count survives.
 *
 * <p><strong>This class exists because of a bug, and the bug is worth stating so nobody undoes the
 * fix.</strong> {@code AuthenticationService.login} is transactional, and it throws for a wrong
 * password. {@code UserAccountService.verifyCredentials} joins that transaction, so the failure it
 * recorded was rolled back by the very exception that reported it: the counter was incremented on a
 * managed entity, the exception marked the transaction for rollback, and the increment was
 * discarded. Over HTTP the column never moved and no account ever locked. Every unit test passed,
 * because a test calling the service directly has no enclosing transaction to roll back.
 *
 * <p>{@link Propagation#REQUIRES_NEW} is the fix, and it has to be a separate bean rather than an
 * annotated method on {@code UserAccountService}: a self-invocation does not pass through the proxy,
 * so the annotation would be silently ignored and the bug would look fixed while behaving exactly as
 * before.
 *
 * <p>The suspended outer transaction is not left holding anything this one needs. The failure path
 * never writes to the user row before reaching here, so the new transaction takes the row lock, uses
 * it and commits, and the outer transaction rolls back afterwards without touching it.
 *
 * <p>The account being locked is deliberately not reported to the caller. {@code verifyCredentials}
 * answers a wrong password with the same generic outcome whether or not it locked the account, so
 * that somebody who does not hold the password learns nothing about the state of the account. Only a
 * caller who then proves the password is told it is locked.
 */
@Component
class LoginFailureRecorder {

    private final UserRepository users;

    LoginFailureRecorder(UserRepository users) {
        this.users = users;
    }

    /**
     * Records one failed attempt against an account.
     *
     * @return true when this failure was the one that locked it, for the caller's log line
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean recordFailure(UUID userId, int maxAttempts, Duration lockDuration, Instant now) {
        // Loaded again rather than passed in: the caller's instance belongs to the
        // caller's persistence context, and mutating it here would write through the
        // transaction that is about to roll back, which is the whole problem.
        return users.findByIdAndDeletedAtIsNull(userId)
                .map(user -> user.recordFailedLogin(maxAttempts, lockDuration, now))
                .orElse(false);
    }
}
