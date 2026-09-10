package com.company.taskmanagementplatform.users;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Bulk lookup, so rendering a page of members costs one query rather than one per row. The
     * member roster is the first place that N+1 would have shown up.
     */
    List<User> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
