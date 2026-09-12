package com.company.taskmanagementplatform.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.security.PasswordPolicy;
import com.company.taskmanagementplatform.support.TestSecurityProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private ApplicationEventPublisher events;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenAnswer(call -> "hashed:" + call.getArgument(0));
        service = new UserAccountService(
                repository,
                encoder,
                new PasswordPolicy(TestSecurityProperties.defaults()),
                TestSecurityProperties.withLockout(5, Duration.ofMinutes(15)),
                events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void normalisesTheAddressBeforeStoringIt() {
        // Both halves of the system have to agree on one form. See Emails.
        when(repository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        UserAccount account = service.register("  Ada@Example.COM ", "password123", "Ada", "Lovelace");

        assertThat(account.email()).isEqualTo("ada@example.com");
    }

    @Test
    void refusesAnAddressThatAlreadyHasAnAccount() {
        when(repository.existsByEmailAndDeletedAtIsNull("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Ada@example.com", "password123", "Ada", "Lovelace"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void appliesThePasswordPolicyBeforeHashing() {
        when(repository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.register("ada@example.com", "short", "Ada", "Lovelace"))
                .isInstanceOf(com.company.taskmanagementplatform.common.error.BadRequestException.class);
    }

    @Test
    void reportsAWrongPasswordAsInvalidCredentials() {
        User user = active("ada@example.com");
        when(repository.findByEmailAndDeletedAtIsNull("ada@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);

        assertThat(service.verifyCredentials("ada@example.com", "wrong").outcome())
                .isEqualTo(CredentialCheck.Outcome.INVALID_CREDENTIALS);
    }

    @Test
    void reportsAnUnknownAddressAsInvalidCredentialsToo() {
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        CredentialCheck check = service.verifyCredentials("nobody@example.com", "whatever");

        assertThat(check.outcome()).isEqualTo(CredentialCheck.Outcome.INVALID_CREDENTIALS);
        // No identifier, because there may be no account and saying so is the thing
        // being avoided.
        assertThat(check.userId()).isNull();
    }

    @Test
    void comparesAgainstADummyHashWhenNoAccountMatches() {
        // Otherwise an unregistered address returns in microseconds and a registered
        // one takes a bcrypt comparison, which enumerates the user base by timing.
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        service.verifyCredentials("nobody@example.com", "whatever");

        verify(encoder, atLeastOnce()).matches(eq("whatever"), anyString());
    }

    @Test
    void doesNotRevealAccountStateUntilThePasswordIsCorrect() {
        User deactivated = active("ada@example.com");
        deactivated.deactivate();
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(deactivated));
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);

        // Wrong password on a switched-off account still reports only the generic
        // failure. Anything else would confirm the address is registered.
        assertThat(service.verifyCredentials("ada@example.com", "wrong").outcome())
                .isEqualTo(CredentialCheck.Outcome.INVALID_CREDENTIALS);
    }

    @Test
    void revealsAccountStateOnceThePasswordIsCorrect() {
        User deactivated = active("ada@example.com");
        deactivated.deactivate();
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(deactivated));
        when(encoder.matches(eq("right"), any())).thenReturn(true);

        assertThat(service.verifyCredentials("ada@example.com", "right").outcome())
                .isEqualTo(CredentialCheck.Outcome.INACTIVE);
    }

    @Test
    void refusesAnUnverifiedAccountWithTheRightPassword() {
        User pending = User.register("ada@example.com", "hash", "Ada", "Lovelace", NOW);
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(pending));
        when(encoder.matches(eq("right"), any())).thenReturn(true);

        assertThat(service.verifyCredentials("ada@example.com", "right").outcome())
                .isEqualTo(CredentialCheck.Outcome.EMAIL_NOT_VERIFIED);
    }

    @Test
    void acceptsTheRightPasswordOnAnActiveAccount() {
        User user = active("ada@example.com");
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(user));
        when(encoder.matches(eq("right"), any())).thenReturn(true);

        assertThat(service.verifyCredentials("ada@example.com", "right").outcome())
                .isEqualTo(CredentialCheck.Outcome.SUCCESS);
    }

    @Test
    void locksAnAccountAfterTheConfiguredNumberOfFailures() {
        User user = active("ada@example.com");
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(user));
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            service.verifyCredentials("ada@example.com", "wrong");
        }

        assertThat(user.isLocked(NOW)).isTrue();
    }

    @Test
    void reportsALockedAccountOnlyWhenThePasswordIsRight() {
        User user = active("ada@example.com");
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(user));
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);
        for (int attempt = 0; attempt < 5; attempt++) {
            service.verifyCredentials("ada@example.com", "wrong");
        }

        when(encoder.matches(eq("right"), any())).thenReturn(true);

        // The lock still refuses entry; it just says so honestly now.
        assertThat(service.verifyCredentials("ada@example.com", "right").outcome())
                .isEqualTo(CredentialCheck.Outcome.LOCKED);
    }

    @Test
    void aSuccessfulSignInClearsTheFailureCount() {
        User user = active("ada@example.com");
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(user));
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);
        service.verifyCredentials("ada@example.com", "wrong");
        service.verifyCredentials("ada@example.com", "wrong");

        when(encoder.matches(eq("right"), any())).thenReturn(true);
        service.verifyCredentials("ada@example.com", "right");

        when(encoder.matches(eq("wrong"), any())).thenReturn(false);
        for (int attempt = 0; attempt < 4; attempt++) {
            service.verifyCredentials("ada@example.com", "wrong");
        }

        // Four failures after a reset, so still one short of the limit.
        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    void repeatedFailuresAfterALockDoNotExtendIt() {
        // Otherwise anybody who knows an address could keep its owner locked out
        // indefinitely, turning a defence against guessing into a denial of service.
        User user = active("ada@example.com");
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(user));
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            service.verifyCredentials("ada@example.com", "wrong");
        }
        Instant lockedAt = user.getLockedUntil();
        assertThat(lockedAt).isNotNull();

        for (int attempt = 0; attempt < 50; attempt++) {
            service.verifyCredentials("ada@example.com", "wrong");
        }

        assertThat(user.getLockedUntil()).isEqualTo(lockedAt);
    }

    @Test
    void failuresDuringALockDoNotInflateTheCounter() {
        User user = active("ada@example.com");
        when(repository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(user));
        when(encoder.matches(eq("wrong"), any())).thenReturn(false);

        for (int attempt = 0; attempt < 20; attempt++) {
            service.verifyCredentials("ada@example.com", "wrong");
        }

        // Five to reach the limit, and nothing counted after that.
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void aLockCanBeEarnedAgainOnceTheFirstOneHasExpired() {
        // Bounded, not permanent. The account is usable again after the period, and
        // a fresh run of failures can lock it again.
        User user = active("ada@example.com");
        user.recordFailedLogin(5, Duration.ofMinutes(15), NOW.minus(Duration.ofHours(1)));
        for (int attempt = 0; attempt < 4; attempt++) {
            user.recordFailedLogin(5, Duration.ofMinutes(15), NOW.minus(Duration.ofHours(1)));
        }
        assertThat(user.isLocked(NOW)).isFalse();

        for (int attempt = 0; attempt < 5; attempt++) {
            user.recordFailedLogin(5, Duration.ofMinutes(15), NOW);
        }

        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void aSuccessfulSignInAfterALockExpiresClearsEverything() {
        User user = active("ada@example.com");
        for (int attempt = 0; attempt < 5; attempt++) {
            user.recordFailedLogin(5, Duration.ofMinutes(15), NOW.minus(Duration.ofHours(1)));
        }

        user.recordSuccessfulLogin(NOW);

        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void removalAnnouncesItselfSoMembershipsCanBeCleanedUp() {
        User user = active("ada@example.com");
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));

        service.softDelete(java.util.UUID.randomUUID());

        // Two effects, two events: sessions end, and rosters lose the row.
        verify(events).publishEvent(any(UserDeactivatedEvent.class));
        verify(events).publishEvent(any(UserDeletedEvent.class));
    }

    @Test
    void deactivationDoesNotRemoveMemberships() {
        // A deactivated person is expected back, so their place is kept.
        User user = active("ada@example.com");
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));

        service.deactivate(java.util.UUID.randomUUID());

        verify(events, org.mockito.Mockito.never()).publishEvent(any(UserDeletedEvent.class));
    }

    @Test
    void deactivationAnnouncesItselfSoSessionsCanBeRevoked() {
        User user = active("ada@example.com");
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));

        service.deactivate(java.util.UUID.randomUUID());

        verify(events).publishEvent(any(UserDeactivatedEvent.class));
    }

    @Test
    void reactivatingAnUnverifiedAccountReturnsItToAwaitingVerification() {
        User pending = User.register("ada@example.com", "hash", "Ada", "Lovelace", NOW);
        pending.deactivate();
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(pending));

        UserAccount reactivated = service.activate(java.util.UUID.randomUUID());

        assertThat(reactivated.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    // --- the guards the admin panel needs, phase nine -----------------------
    //
    // The last-administrator refusal lives here rather than in an integration
    // test because it turns on how many accounts hold the platform role, and the
    // suite shares one container and one SUPER_ADMIN role: other tests'
    // administrators are always present, so the count is never one in an IT.
    // Here the count is a stub, which is exactly the kind of thing a unit test
    // is for.

    @Test
    void theLastPlatformAdministratorCannotBeDeactivated() {
        java.util.UUID roleId = java.util.UUID.randomUUID();
        User onlyOne = withPlatformRole("admin@example.com", roleId);
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(onlyOne));
        when(repository.countByPlatformRoleIdAndDeletedAtIsNull(roleId)).thenReturn(1L);

        // An installation with no platform administrator cannot be administered,
        // and SuperAdminBootstrap deliberately never resurrects one.
        assertThatThrownBy(() -> service.deactivate(java.util.UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theLastPlatformAdministratorCannotBeRemoved() {
        java.util.UUID roleId = java.util.UUID.randomUUID();
        User onlyOne = withPlatformRole("admin@example.com", roleId);
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(onlyOne));
        when(repository.countByPlatformRoleIdAndDeletedAtIsNull(roleId)).thenReturn(1L);

        assertThatThrownBy(() -> service.softDelete(java.util.UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theLastPlatformAdministratorCannotBeDemoted() {
        java.util.UUID roleId = java.util.UUID.randomUUID();
        User onlyOne = withPlatformRole("admin@example.com", roleId);
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(onlyOne));
        when(repository.countByPlatformRoleIdAndDeletedAtIsNull(roleId)).thenReturn(1L);

        assertThatThrownBy(() -> service.assignPlatformRole(java.util.UUID.randomUUID(), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void oneOfSeveralPlatformAdministratorsMayBeDeactivated() {
        java.util.UUID roleId = java.util.UUID.randomUUID();
        User oneOfTwo = withPlatformRole("admin@example.com", roleId);
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(oneOfTwo));
        when(repository.countByPlatformRoleIdAndDeletedAtIsNull(roleId)).thenReturn(2L);

        // The rule is about the last one, not about administrators in general.
        assertThat(service.deactivate(java.util.UUID.randomUUID()).status()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void anOrdinaryAccountIsNeverCheckedAgainstTheAdministratorCount() {
        User ordinary = active("ada@example.com");
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(ordinary));

        service.deactivate(java.util.UUID.randomUUID());

        // No platform role means no question to ask, and no query to pay for.
        verify(repository, org.mockito.Mockito.never()).countByPlatformRoleIdAndDeletedAtIsNull(any());
    }

    @Test
    void unlockingClearsBothColumnsAndSaysWhetherAnythingHappened() {
        User locked = active("ada@example.com");
        locked.recordFailedLogin(1, Duration.ofMinutes(15), NOW);
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(locked));

        java.util.UUID actor = java.util.UUID.randomUUID();
        service.unlock(actor, java.util.UUID.randomUUID());

        assertThat(locked.getLockedUntil()).isNull();
        assertThat(locked.getFailedLoginAttempts()).isZero();
        verify(events).publishEvent(any(UserAdminEvents.Unlocked.class));
    }

    @Test
    void unlockingAnAccountThatWasNotLockedAnnouncesNothing() {
        User never = active("ada@example.com");
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(never));

        service.unlock(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());

        // Idempotent, and silent. An audit trail with a row for every no-op is a
        // trail nobody reads.
        verify(events, org.mockito.Mockito.never()).publishEvent(any(UserAdminEvents.Unlocked.class));
    }

    @Test
    void theAdministrativeProfileEditAnnouncesItselfAndTheSelfServiceOneDoesNot() {
        User user = active("ada@example.com");
        when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));

        service.updateProfile(java.util.UUID.randomUUID(), "Ada", "Byron");
        verify(events, org.mockito.Mockito.never()).publishEvent(any(UserAdminEvents.ProfileUpdated.class));

        // Two methods rather than one that decides at runtime which it was, so a
        // person renaming themselves is never recorded as an administrative action.
        service.updateProfileOf(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Grace", "Hopper");
        verify(events).publishEvent(any(UserAdminEvents.ProfileUpdated.class));
    }

    private User active(String email) {
        User user = User.register(email, "hashed:right", "Ada", "Lovelace", NOW);
        user.markEmailVerified(NOW);
        return user;
    }

    private User withPlatformRole(String email, java.util.UUID roleId) {
        User user = active(email);
        user.assignPlatformRole(roleId);
        return user;
    }
}
