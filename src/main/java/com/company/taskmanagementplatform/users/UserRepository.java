package com.company.taskmanagementplatform.users;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Package-private, like the entity it returns. Nothing outside {@code users} may reach the table.
 *
 * <p>Every method filters removed rows. A deleted account is invisible rather than merely flagged,
 * so no caller has to remember to exclude it.
 */
interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    /**
     * Two methods rather than one with nullable parameters.
     *
     * <p>The tidier-looking single query would need {@code :status IS NULL} and {@code :search IS
     * NULL} clauses, and a bare null parameter in that position gives PostgreSQL nothing to infer a
     * type from. Passing an always-present pattern and choosing the method in the service keeps every
     * parameter typed by its use.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (LOWER(u.email) LIKE :pattern
                   OR LOWER(u.firstName) LIKE :pattern
                   OR LOWER(u.lastName) LIKE :pattern)
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND u.status = :status
              AND (LOWER(u.email) LIKE :pattern
                   OR LOWER(u.firstName) LIKE :pattern
                   OR LOWER(u.lastName) LIKE :pattern)
            """)
    Page<User> searchByStatus(
            @Param("pattern") String pattern, @Param("status") UserStatus status, Pageable pageable);

    boolean existsByPlatformRoleIdAndDeletedAtIsNull(UUID platformRoleId);

    /**
     * How many live accounts hold a platform role.
     *
     * <p>Not the same question as the {@code exists} above, and the difference is the whole point:
     * the admin panel has to refuse demoting, deactivating or deleting the <em>last</em> platform
     * administrator, and an installation with none is unadministrable until somebody edits the
     * database by hand. {@code SuperAdminBootstrap} deliberately never resurrects one.
     */
    long countByPlatformRoleIdAndDeletedAtIsNull(UUID platformRoleId);

    /**
     * The administrative directory, narrowed the four ways the admin panel needs.
     *
     * <p>Four methods rather than one with nullable parameters, for the reason the pair above
     * already gives: a bare null in a typed position gives PostgreSQL nothing to infer from. The
     * service picks the method, so every parameter stays typed by its use.
     *
     * <p>"Locked" is a live lockout rather than a lockout that has since expired, which is why the
     * moment is a parameter rather than {@code now()} inside the query: the service already holds a
     * clock and one request's figures should agree with each other.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND u.lockedUntil IS NOT NULL AND u.lockedUntil > :now
              AND (LOWER(u.email) LIKE :pattern
                   OR LOWER(u.firstName) LIKE :pattern
                   OR LOWER(u.lastName) LIKE :pattern)
            """)
    Page<User> searchLocked(
            @Param("pattern") String pattern, @Param("now") java.time.Instant now, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND u.status = :status
              AND u.lockedUntil IS NOT NULL AND u.lockedUntil > :now
              AND (LOWER(u.email) LIKE :pattern
                   OR LOWER(u.firstName) LIKE :pattern
                   OR LOWER(u.lastName) LIKE :pattern)
            """)
    Page<User> searchLockedByStatus(
            @Param("pattern") String pattern,
            @Param("status") UserStatus status,
            @Param("now") java.time.Instant now,
            Pageable pageable);

    /** How many live accounts hold each status, in one query. Rows of {@code [status, count]}. */
    @Query("SELECT u.status, count(u) FROM User u WHERE u.deletedAt IS NULL GROUP BY u.status")
    List<Object[]> countByStatusGrouped();

    long countByDeletedAtIsNull();

    /** Live accounts whose lockout has not yet expired. */
    long countByDeletedAtIsNullAndLockedUntilGreaterThan(java.time.Instant now);

    long countByDeletedAtIsNullAndCreatedAtGreaterThanEqual(java.time.Instant since);

    long countByDeletedAtIsNullAndLastLoginAtGreaterThanEqual(java.time.Instant since);

    /**
     * Bulk lookup, so rendering a page of members costs one query rather than one per row. The
     * member roster is the first place that N+1 would have shown up.
     */
    /**
     * Reads an account for approval, holding a write lock on the row.
     *
     * <p>The lock is what makes double approval impossible. Two administrators working the queue at
     * the same moment would otherwise both read {@code PENDING_APPROVAL}, both decide to proceed,
     * and both grant a membership — leaving somebody approved into two workspaces that nobody chose
     * together. With the lock the second transaction waits for the first to commit and then sees an
     * account that is no longer pending, so {@link User#approve} returns false and the caller stops.
     *
     * <p>A plain read would not do: the check and the write have to be one step as far as any other
     * transaction is concerned, and only the database can arrange that.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId AND u.deletedAt IS NULL")
    Optional<User> findForApproval(@Param("userId") UUID userId);

    List<User> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
