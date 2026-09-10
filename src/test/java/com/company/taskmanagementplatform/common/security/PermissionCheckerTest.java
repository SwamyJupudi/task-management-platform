package com.company.taskmanagementplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class PermissionCheckerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    private final PermissionResolver resolver = mock(PermissionResolver.class);
    private final PermissionChecker checker = new PermissionChecker(resolver);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantsAPermissionTheCallerHoldsInThatWorkspace() {
        signedIn();
        when(resolver.resolveForWorkspace(USER_ID, WORKSPACE_ID)).thenReturn(Set.of(Permissions.MEMBER_INVITE));

        assertThat(checker.inWorkspace(WORKSPACE_ID, Permissions.MEMBER_INVITE)).isTrue();
    }

    @Test
    void refusesAPermissionTheCallerDoesNotHold() {
        signedIn();
        when(resolver.resolveForWorkspace(USER_ID, WORKSPACE_ID)).thenReturn(Set.of(Permissions.MEMBER_READ));

        assertThat(checker.inWorkspace(WORKSPACE_ID, Permissions.MEMBER_INVITE)).isFalse();
    }

    @Test
    void refusesAnonymousCallersWithoutAskingTheResolver() {
        assertThat(checker.inWorkspace(WORKSPACE_ID, Permissions.MEMBER_READ)).isFalse();
        assertThat(checker.onPlatform(Permissions.USER_READ)).isFalse();

        verify(resolver, never()).resolveForWorkspace(any(), any());
        verify(resolver, never()).resolveForPlatform(any());
    }

    @Test
    void refusesAMissingWorkspaceRatherThanResolvingNothing() {
        signedIn();

        assertThat(checker.inWorkspace(null, Permissions.MEMBER_READ)).isFalse();
    }

    @Test
    void platformChecksIgnoreWorkspaceMembership() {
        // Administering one workspace must never reach across the installation, so
        // the platform question is asked of a different method entirely.
        signedIn();
        when(resolver.resolveForPlatform(USER_ID)).thenReturn(Set.of());
        when(resolver.resolveForWorkspace(any(), any())).thenReturn(Set.of(Permissions.USER_READ));

        assertThat(checker.onPlatform(Permissions.USER_READ)).isFalse();
    }

    @Test
    void recognisesTheCallerAsThemselves() {
        signedIn();

        assertThat(checker.isSelf(USER_ID)).isTrue();
        assertThat(checker.isSelf(UUID.randomUUID())).isFalse();
    }

    private void signedIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(USER_ID), null, List.of()));
    }
}
