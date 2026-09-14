package com.company.taskmanagementplatform.auth;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ForbiddenException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.error.UnauthorizedException;
import com.company.taskmanagementplatform.common.ratelimit.AccountRateLimitGuard;
import com.company.taskmanagementplatform.common.security.AccessTokenService;
import com.company.taskmanagementplatform.users.CredentialCheck;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Signing in, refreshing and signing out.
 *
 * <p>This class never sees a password hash. It asks {@code users} whether a credential is good and
 * receives an outcome, which is what keeps the hash inside one package.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserAccountService users;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final AccountRateLimitGuard rateLimits;

    AuthenticationService(
            UserAccountService users,
            AccessTokenService accessTokens,
            RefreshTokenService refreshTokens,
            AccountRateLimitGuard rateLimits) {
        this.users = users;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.rateLimits = rateLimits;
    }

    /**
     * Verifies a credential and starts a session.
     *
     * @throws UnauthorizedException with one generic message for any wrong pair
     * @throws ForbiddenException when the password was right but the account cannot sign in
     */
    @Transactional
    public Session login(String email, String password, String userAgent, String ipAddress) {
        // Before the credential check, so the limit bounds the bcrypt comparisons
        // rather than merely the replies. Deliberately looser than the account
        // lockout, so the lockout still fires first and somebody who has mistyped
        // their password is told their account is locked rather than told to come
        // back later; RateLimitProperties sets out that reasoning in full.
        rateLimits.checkLogin(email);

        CredentialCheck check = users.verifyCredentials(email, password);

        switch (check.outcome()) {
            case INVALID_CREDENTIALS -> {
                // No user id: there may not be an account, and saying so is the
                // thing being avoided.
                log.warn("Failed sign-in attempt");
                throw UnauthorizedException.invalidCredentials();
            }
            case LOCKED -> {
                log.warn("Sign-in refused, account locked: userId={}", check.userId());
                throw ForbiddenException.accountLocked();
            }
            case EMAIL_NOT_VERIFIED -> throw ForbiddenException.emailNotVerified();
            case INACTIVE -> {
                log.warn("Sign-in refused, account inactive: userId={}", check.userId());
                throw ForbiddenException.accountInactive();
            }
            case SUCCESS -> {
                // fall through
            }
        }

        UserAccount account = users.findById(check.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", check.userId()));

        return issueSession(account, userAgent, ipAddress);
    }

    /**
     * Exchanges a refresh token for a new pair, rotating it.
     *
     * <p>The account is re-read rather than trusted from the old token, so a deactivation takes effect
     * here as well as on ordinary requests.
     */
    @Transactional
    public Session refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        RefreshTokenService.RotatedRefreshToken rotated =
                refreshTokens.rotate(rawRefreshToken, userAgent, ipAddress);

        UserAccount account = users.findById(rotated.userId()).orElseThrow(UnauthorizedException::tokenInvalid);
        if (account.status() != com.company.taskmanagementplatform.users.UserStatus.ACTIVE) {
            refreshTokens.revokeAllForUser(account.id(), RevocationReason.ACCOUNT_DEACTIVATED);
            throw ForbiddenException.accountInactive();
        }

        AccessTokenService.IssuedAccessToken accessToken = accessTokens.issue(account.id());
        return new Session(account, accessToken, rotated.token());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null) {
            refreshTokens.revokeByRawToken(rawRefreshToken, RevocationReason.LOGOUT);
        }
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        refreshTokens.revokeAllForUser(userId, RevocationReason.LOGOUT_ALL);
    }

    /** Issues a fresh pair without checking a password, for the caller who has just changed one. */
    @Transactional
    public Session reissue(UUID userId, String userAgent, String ipAddress) {
        UserAccount account =
                users.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        return issueSession(account, userAgent, ipAddress);
    }

    private Session issueSession(UserAccount account, String userAgent, String ipAddress) {
        return new Session(
                account,
                accessTokens.issue(account.id()),
                refreshTokens.issue(account.id(), userAgent, ipAddress));
    }

    /** A signed-in session: who it belongs to, and the two tokens it was given. */
    public record Session(
            UserAccount account,
            AccessTokenService.IssuedAccessToken accessToken,
            RefreshTokenService.IssuedRefreshToken refreshToken) {}
}
