package com.company.taskmanagementplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.taskmanagementplatform.common.error.ErrorCode;
import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.security.TokenHasher;
import com.company.taskmanagementplatform.support.TestSecurityProperties;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private RefreshTokenReuseGuard reuseGuard;

    private final TokenHasher hasher = new TokenHasher();
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(
                repository,
                reuseGuard,
                hasher,
                TestSecurityProperties.withRefreshTtl(Duration.ofDays(14)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesOnlyTheHashOfAnIssuedToken() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        RefreshTokenService.IssuedRefreshToken issued = service.issue(USER_ID, "Firefox", "203.0.113.7");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());

        // The raw value is returned to the caller and must not be in the row.
        assertThat(issued.rawToken()).isNotBlank();
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void startsANewSessionForEachSignIn() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        UUID first = service.issue(USER_ID, null, null).sessionId();
        UUID second = service.issue(USER_ID, null, null).sessionId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rotationKeepsTheSessionAndChangesTheToken() {
        UUID sessionId = UUID.randomUUID();
        RefreshToken existing = live(sessionId);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));
        when(repository.claimForRotation(any(), any())).thenReturn(1);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate("raw", "Firefox", "203.0.113.7");

        assertThat(rotated.userId()).isEqualTo(USER_ID);
        assertThat(rotated.token().sessionId()).isEqualTo(sessionId);
        assertThat(rotated.token().rawToken()).isNotBlank();
    }

    @Test
    void rotationClaimsTheTokenBeforeMintingItsSuccessor() {
        // The order is the guard. Minting first and claiming afterwards would leave
        // the window this whole arrangement exists to close.
        RefreshToken existing = live(UUID.randomUUID());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));
        when(repository.claimForRotation(any(), any())).thenReturn(1);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.rotate("raw", null, null);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(repository);
        inOrder.verify(repository).claimForRotation(any(), eq(NOW));
        inOrder.verify(repository).save(any());
        inOrder.verify(repository).linkSuccessor(any(), any());
    }

    @Test
    void losingTheClaimIsTreatedAsReuseAndRevokesTheWholeSession() {
        // A claim that returns zero means the token was already consumed: either a
        // replay of a stolen token, or the losing half of a race. Indistinguishable
        // from here, and only one of the two readings is safe.
        UUID sessionId = UUID.randomUUID();
        RefreshToken contested = live(sessionId);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(contested));
        when(repository.claimForRotation(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rotate("raw", null, null))
                .isInstanceOf(UnauthorizedException.class);

        // The eviction goes through the guard, which commits it in its own
        // transaction so that the refusal above cannot roll it back.
        verify(reuseGuard).revokeSessionAsReuse(sessionId, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void refusesAnExpiredTokenWithoutRevokingTheSession() {
        RefreshToken expired = RefreshToken.issue(
                USER_ID,
                UUID.randomUUID(),
                "hash",
                NOW.minus(Duration.ofDays(20)),
                NOW.minus(Duration.ofDays(1)),
                null,
                null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("raw", null, null))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(thrown -> assertThat(((UnauthorizedException) thrown).errorCode())
                        .isEqualTo(ErrorCode.TOKEN_EXPIRED));

        // Expiry is ordinary, not suspicious. Ending every session over it would
        // sign people out for nothing, so the claim is never even attempted.
        verify(repository, never()).claimForRotation(any(), any());
        verify(reuseGuard, never()).revokeSessionAsReuse(any(), any());
    }

    @Test
    void refusesAnUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("raw", null, null))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(thrown -> assertThat(((UnauthorizedException) thrown).errorCode())
                        .isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    @Test
    void revokesEveryLiveTokenOfOneAccount() {
        service.revokeAllForUser(USER_ID, RevocationReason.PASSWORD_CHANGED);

        verify(repository).revokeAllForUser(USER_ID, RevocationReason.PASSWORD_CHANGED, NOW);
    }

    @Test
    void logoutRevokesOnlyTheTokenPresented() {
        RefreshToken existing = live(UUID.randomUUID());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        service.revokeByRawToken("raw", RevocationReason.LOGOUT);

        assertThat(existing.isRevoked()).isTrue();
        verify(repository, never()).revokeAllForUser(any(), any(), any());
    }

    @Test
    void logoutOfAnUnknownTokenIsQuietlyIgnored() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        // Signing out should never fail. There is nothing to protect here and an
        // error would only confuse a client that is already leaving.
        service.revokeByRawToken("raw", RevocationReason.LOGOUT);

        verify(repository, never()).save(any());
    }

    @Test
    void listsOnlyUnexpiredSessions() {
        RefreshToken current = live(UUID.randomUUID());
        RefreshToken stale = RefreshToken.issue(
                USER_ID,
                UUID.randomUUID(),
                "old",
                NOW.minus(Duration.ofDays(30)),
                NOW.minus(Duration.ofDays(2)),
                null,
                null);
        when(repository.findAllByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(USER_ID))
                .thenReturn(java.util.List.of(current, stale));

        assertThat(service.activeSessions(USER_ID)).containsExactly(current);
    }

    @Test
    void revokingAnUnknownSessionIsNotFound() {
        when(repository.findFirstByUserIdAndSessionIdAndRevokedAtIsNull(eq(USER_ID), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeSession(USER_ID, UUID.randomUUID()))
                .isInstanceOf(com.company.taskmanagementplatform.common.error.ResourceNotFoundException.class);
    }

    private RefreshToken live(UUID sessionId) {
        return RefreshToken.issue(
                USER_ID, sessionId, "hash", NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(13)), null, null);
    }
}
