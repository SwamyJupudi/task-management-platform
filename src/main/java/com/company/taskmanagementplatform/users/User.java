package com.company.taskmanagementplatform.users;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A person with an account.
 *
 * <p>Package-private on purpose. This class and its repository never leave the {@code users} module;
 * everything outside talks to {@link UserAccountService} and receives {@link UserAccount}, which
 * carries no password hash. That is what stops the hash from becoming a value other modules can hold
 * and accidentally log.
 *
 * <p>The setters are deliberately few. State changes go through named methods that keep related
 * fields consistent, so it is not possible to mark an account verified without also giving it a
 * verification time.
 */
@Entity
@Table(name = "users")
class User {

    /**
     * Mirrors {@code RoleScope.PLATFORM}, which belongs to another module and so cannot be imported
     * here. The value is pinned by a check constraint in {@code V2} and by {@code EnumConstraintIT}.
     */
    private static final String PLATFORM_SCOPE = "PLATFORM";

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Always stored normalised. See {@code Emails}. */
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "platform_role_id")
    private UUID platformRoleId;

    /**
     * Always {@code PLATFORM} when a platform role is held, and null when it is not.
     *
     * <p>Never set by a caller. It exists so the database key can reference {@code roles (id,
     * scope)} instead of {@code roles (id)}, which is what makes assigning a workspace role here a
     * write PostgreSQL refuses rather than a rule a service has to remember. See {@code V2}.
     */
    @Column(name = "platform_role_scope")
    private String platformRoleScope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
        // for JPA
    }

    static User register(String normalizedEmail, String passwordHash, String firstName, String lastName, Instant now) {
        User user = new User();
        user.email = normalizedEmail;
        user.passwordHash = passwordHash;
        user.firstName = firstName;
        user.lastName = lastName;
        user.status = UserStatus.PENDING_APPROVAL;
        user.passwordChangedAt = now;
        user.failedLoginAttempts = 0;
        return user;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (passwordChangedAt == null) {
            passwordChangedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- state changes ----------------------------------------------------

    /**
     * Records that the address was confirmed.
     *
     * <p>No longer touches the status, and that is the whole of what onboarding by approval changed
     * here. Confirming an address used to be what activated an account; an administrator's approval
     * is what does that now, and this column is a record of what happened rather than a gate. The
     * {@code users_verified_when_active_check} constraint that tied the two together is dropped in
     * {@code V14__approval_onboarding.sql}.
     */
    void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
    }

    /**
     * Lets somebody in, and records who did.
     *
     * <p>Guarded on {@code PENDING_APPROVAL}, which is what makes approving twice harmless and what
     * stops an approval reviving a {@code DEACTIVATED} account: switching somebody off stays a
     * decision only {@link #activate()} reverses.
     *
     * <p>The guard holds under a race because the caller reads this row with a write lock first, so
     * a second administrator approving the same registration waits, then finds it no longer pending.
     * See {@code UserRepository.findPendingForUpdate}.
     *
     * @return true when this call is what changed the status, so the caller knows whether to grant
     *     the membership
     */
    boolean approve(UUID approverUserId, Instant now) {
        if (status != UserStatus.PENDING_APPROVAL) {
            return false;
        }
        status = UserStatus.ACTIVE;
        approvedAt = now;
        approvedByUserId = approverUserId;
        return true;
    }

    void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        // Read by the authentication filter to refuse tokens minted before this
        // moment. It is what makes a password change end other sessions.
        this.passwordChangedAt = now;
        clearLock();
    }

    void recordSuccessfulLogin(Instant now) {
        this.lastLoginAt = now;
        clearLock();
    }

    /**
     * Counts a failed sign-in, locking the account once the limit is reached.
     *
     * <p>A lock is never extended by further failures, which is the part that matters. Re-arming it
     * on every attempt would let anybody who knows an address keep its owner locked out for as long
     * as they cared to keep typing, turning a protection against guessing into a way of denying
     * somebody their account. The bound is therefore one lock period per run of failures: an
     * attacker can cause another lock, but only by waiting out the first and starting again.
     *
     * <p>Once a lock has expired the count starts from zero, so unrelated failures weeks apart do
     * not accumulate into a lock.
     *
     * @return true when this failure was the one that locked the account
     */
    boolean recordFailedLogin(int maxAttempts, java.time.Duration lockDuration, Instant now) {
        if (isLocked(now)) {
            return false;
        }
        if (lockedUntil != null) {
            // The previous lock has run out. This is the start of a fresh run.
            lockedUntil = null;
            failedLoginAttempts = 0;
        }

        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    void clearLock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /**
     * Clears a lockout by hand, for the administrator who does not want to make somebody wait.
     *
     * <p>The same two columns a successful sign-in clears, which is deliberate: an unlock is the
     * administrator saying what a successful sign-in would have said. It is named separately from
     * {@link #clearLock()} only so the call site reads as the verb the panel offers.
     *
     * @return true when there was a lockout to clear, so the caller knows whether anything happened
     */
    boolean unlock() {
        boolean wasLocked = lockedUntil != null || failedLoginAttempts > 0;
        clearLock();
        return wasLocked;
    }

    void deactivate() {
        status = UserStatus.DEACTIVATED;
    }

    /**
     * Switches a deactivated account back on.
     *
     * <p>An account that was never approved returns to the queue rather than straight to active, so
     * reactivating somebody cannot be a way of skipping the approval an administrator never gave.
     * Previously this asked whether the address had been confirmed, which is no longer the question
     * that decides whether somebody may work.
     */
    void activate() {
        status = approvedAt == null ? UserStatus.PENDING_APPROVAL : UserStatus.ACTIVE;
    }

    void softDelete(Instant now) {
        deletedAt = now;
    }

    void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /**
     * Points this account at a platform role.
     *
     * <p>The scope is written here rather than accepted from the caller, so the pair is always
     * consistent and the composite foreign key is always checked. If {@code roleId} names anything
     * other than a platform-scoped role, the flush fails.
     */
    void assignPlatformRole(UUID roleId) {
        this.platformRoleId = roleId;
        this.platformRoleScope = roleId == null ? null : PLATFORM_SCOPE;
    }

    // --- queries ----------------------------------------------------------

    boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Whether a token issued for this account should still be honoured. */
    /**
     * Whether a token issued for this account is still honoured.
     *
     * <p>{@code PENDING_APPROVAL} counts, which is what lets somebody who has just registered sign
     * in and be told they are waiting. It grants them nothing: every workspace, project and task is
     * reached through a membership, and an unapproved account has none. {@code DEACTIVATED} and a
     * removed account are refused, as before.
     */
    boolean isUsable() {
        return deletedAt == null && (status == UserStatus.ACTIVE || status == UserStatus.PENDING_APPROVAL);
    }

    Instant getApprovedAt() {
        return approvedAt;
    }

    UUID getApprovedByUserId() {
        return approvedByUserId;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getFirstName() {
        return firstName;
    }

    String getLastName() {
        return lastName;
    }

    UserStatus getStatus() {
        return status;
    }

    Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    Instant getLastLoginAt() {
        return lastLoginAt;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }

    int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    UUID getPlatformRoleId() {
        return platformRoleId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }
}
