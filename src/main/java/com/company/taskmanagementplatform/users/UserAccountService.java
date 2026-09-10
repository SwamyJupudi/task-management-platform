package com.company.taskmanagementplatform.users;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.PasswordPolicy;
import com.company.taskmanagementplatform.common.security.SecurityProperties;
import com.company.taskmanagementplatform.common.util.Emails;

/**
 * The only way in to an account, for this module and every other.
 *
 * <p>Password hashing lives here rather than in {@code auth}, which is the one boundary decision in
 * the identity phase worth stating out loud. It looks misplaced until you follow the alternative: if
 * {@code auth} verified passwords, it would need the hash, the hash would cross a module boundary,
 * and the guarantee that only one package can ever hold it would be gone. Instead {@code auth} asks
 * {@link #verifyCredentials} a question and receives {@link CredentialCheck}, an outcome.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityProperties.Lockout lockout;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * A real hash of a value nobody knows, compared against when no account matches.
     *
     * <p>Without it, a sign-in attempt for an unregistered address would return in microseconds while
     * a registered one would take the time of a bcrypt comparison, and that difference is enough to
     * enumerate the user base without ever guessing a password.
     */
    private final String timingEqualisationHash;

    UserAccountService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SecurityProperties securityProperties,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.lockout = securityProperties.lockout();
        this.events = events;
        this.clock = clock;
        this.timingEqualisationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // --- registration -----------------------------------------------------

    @Transactional
    public UserAccount register(String email, String rawPassword, String firstName, String lastName) {
        return register(email, rawPassword, firstName, lastName, false);
    }

    /**
     * Creates an account.
     *
     * @param alreadyVerified true only when the address was proved some other way. Accepting an
     *     invitation is the one case: the token was delivered to that address and nothing else, so
     *     redeeming it demonstrates the same thing a verification message would, and sending a second
     *     message to confirm what has just been confirmed would be theatre.
     */
    @Transactional
    public UserAccount register(
            String email, String rawPassword, String firstName, String lastName, boolean alreadyVerified) {
        String normalized = Emails.normalize(email);
        passwordPolicy.validate(rawPassword);

        if (users.existsByEmailAndDeletedAtIsNull(normalized)) {
            throw new ConflictException("An account already exists for that email address.");
        }

        User user = User.register(
                normalized, passwordEncoder.encode(rawPassword), firstName.trim(), lastName.trim(), clock.instant());
        if (alreadyVerified) {
            user.markEmailVerified(clock.instant());
        }
        return toAccount(users.save(user));
    }

    // --- authentication ---------------------------------------------------

    /**
     * Checks a password and then, only if it was right, the state of the account.
     *
     * <p>Writes as well as reads: a failure advances the lockout counter and a success clears it,
     * which is why this is not a read-only transaction.
     */
    @Transactional
    public CredentialCheck verifyCredentials(String email, String rawPassword) {
        Optional<User> found = users.findByEmailAndDeletedAtIsNull(Emails.normalize(email));

        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, timingEqualisationHash);
            return CredentialCheck.invalid();
        }

        User user = found.get();
        Instant now = clock.instant();

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            boolean nowLocked = user.recordFailedLogin(lockout.maxAttempts(), lockout.duration(), now);
            if (nowLocked) {
                log.warn("Account locked after {} consecutive failures: userId={}", lockout.maxAttempts(), user.getId());
            }
            return CredentialCheck.invalid();
        }

        // Past this line the caller has proved they hold the password, so telling
        // them why they still cannot sign in gives away nothing they did not have.
        if (user.isLocked(now)) {
            return new CredentialCheck(CredentialCheck.Outcome.LOCKED, user.getId());
        }
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            return new CredentialCheck(CredentialCheck.Outcome.INACTIVE, user.getId());
        }
        if (user.getEmailVerifiedAt() == null) {
            return new CredentialCheck(CredentialCheck.Outcome.EMAIL_NOT_VERIFIED, user.getId());
        }

        user.recordSuccessfulLogin(now);
        return new CredentialCheck(CredentialCheck.Outcome.SUCCESS, user.getId());
    }

    @Transactional(readOnly = true)
    public boolean matchesCurrentPassword(UUID userId, String rawPassword) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .map(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .orElse(false);
    }

    // --- lifecycle --------------------------------------------------------

    @Transactional
    public void markEmailVerified(UUID userId) {
        require(userId).markEmailVerified(clock.instant());
    }

    /**
     * Sets a new password and stamps the moment.
     *
     * <p>Ending other sessions is not done here. The stamp is what the authentication filter compares
     * an access token against, and revoking refresh tokens is {@code auth}'s work, which it does in
     * the same transaction as the caller.
     */
    @Transactional
    public void changePassword(UUID userId, String newRawPassword) {
        passwordPolicy.validate(newRawPassword);
        require(userId).changePassword(passwordEncoder.encode(newRawPassword), clock.instant());
    }

    @Transactional
    public UserAccount deactivate(UUID userId) {
        User user = require(userId);
        user.deactivate();
        // Listened for inside the same transaction by auth, which revokes the
        // sessions. Deactivating and leaving a session alive would be worse than
        // not deactivating at all.
        events.publishEvent(new UserDeactivatedEvent(userId));
        return toAccount(user);
    }

    @Transactional
    public UserAccount activate(UUID userId) {
        User user = require(userId);
        user.activate();
        return toAccount(user);
    }

    @Transactional
    public void softDelete(UUID userId) {
        User user = require(userId);
        user.softDelete(clock.instant());
        // Two effects, two events. Sessions end because the account can no longer
        // be used, which is also true of a deactivation. Memberships are removed
        // because the person is gone, which is not.
        events.publishEvent(new UserDeactivatedEvent(userId));
        events.publishEvent(new UserDeletedEvent(userId));
    }

    @Transactional
    public UserAccount updateProfile(UUID userId, String firstName, String lastName) {
        User user = require(userId);
        user.updateProfile(firstName.trim(), lastName.trim());
        return toAccount(user);
    }

    /**
     * Points an account at a platform role.
     *
     * <p>Not the method to call. {@code PlatformRoleService.assignSuperAdmin} is the one application
     * callers use, and it takes no role identifier at all, so there is no ordinary path by which a
     * wrong role could be named. This is the writer behind it, and it lives here because only this
     * module may touch the table.
     *
     * <p>Passing a workspace role does not corrupt anything either: the entity writes the scope
     * itself and the composite foreign key in {@code V2} refuses the flush. The guarantee is the
     * database's, not this method's.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException if the role is not
     *     platform-scoped
     */
    @Transactional
    public void assignPlatformRole(UUID userId, UUID platformRoleId) {
        require(userId).assignPlatformRole(platformRoleId);
        users.flush();
    }

    // --- reads ------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId).map(UserAccountService::toAccount);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return users.findByEmailAndDeletedAtIsNull(Emails.normalize(email)).map(UserAccountService::toAccount);
    }

    /** One query for many accounts, so a roster does not fetch them one at a time. */
    @Transactional(readOnly = true)
    public Map<UUID, UserAccount> findAllByIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserAccount> found = users.findAllByIdInAndDeletedAtIsNull(userIds).stream()
                .map(UserAccountService::toAccount)
                .toList();
        return found.stream().collect(Collectors.toMap(UserAccount::id, Function.identity()));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return users.existsByEmailAndDeletedAtIsNull(Emails.normalize(email));
    }

    @Transactional(readOnly = true)
    public boolean anyoneHoldsPlatformRole(UUID roleId) {
        return users.existsByPlatformRoleIdAndDeletedAtIsNull(roleId);
    }

    private User require(UUID userId) {
        return users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    static UserAccount toAccount(User user) {
        return new UserAccount(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                user.getPlatformRoleId(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
