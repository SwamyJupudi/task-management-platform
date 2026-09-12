package com.company.taskmanagementplatform.users;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the users module publishes to {@code admin}, and nothing more.
 *
 * <p>A third class beside {@link UserAccountService} and {@link UserQueryService}, for the reason
 * every facade in this platform gives. Those two serve this module's own controller and map to types
 * it owns; this one answers with counts another module composes into a different shape. Nothing here
 * authorizes anything: the caller has already passed a platform permission check, and a facade that
 * could refuse would be an invitation to call it from a path that never checked.
 *
 * <p><strong>Read-only, always.</strong> No entity leaves this class, and every aggregate is
 * computed in SQL. No method loads a collection of accounts so that another module can count them,
 * which is the requirements' instruction about not fetching records unnecessarily applied to the one
 * table in the platform that has no tenant column to narrow by.
 *
 * <p>Every figure counts live accounts only. A soft-deleted account is gone rather than flagged,
 * everywhere, per the convention {@code database.md} records.
 */
@Service
public class UserAdminFacade {

    private final UserRepository users;

    UserAdminFacade(UserRepository users) {
        this.users = users;
    }

    /**
     * How many live accounts hold each status.
     *
     * <p><strong>Every status is present, including those nobody holds.</strong> The grouped query
     * returns no row for an empty status, which is right for it and wrong for the panel this feeds:
     * a missing entry would make a client know the status list to draw the chart, and a status that
     * quietly vanished when its last holder was verified would read as one that was never there.
     * The gap is filled here rather than in {@code admin}, because this is where the enumeration
     * lives; asking for it further up would be a second place for it to go stale.
     *
     * <p>This mirrors {@code MembershipService.countMembersByRole} exactly, and for the same reason.
     */
    @Transactional(readOnly = true)
    public Map<UserStatus, Long> countByStatus() {
        Map<UserStatus, Long> counts = new EnumMap<>(UserStatus.class);
        for (UserStatus status : UserStatus.values()) {
            counts.put(status, 0L);
        }

        for (Object[] row : users.countByStatusGrouped()) {
            counts.merge((UserStatus) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        return Map.copyOf(counts);
    }

    @Transactional(readOnly = true)
    public long countTotal() {
        return users.countByDeletedAtIsNull();
    }

    /**
     * How many accounts are locked out right now.
     *
     * <p>A different fact from {@code DEACTIVATED} and usually the one an administrator is actually
     * looking for: a lock is a temporary machine decision and a deactivation is a durable human one,
     * which is why they live in different columns.
     *
     * @param now the moment to measure against, passed in rather than read here so one response's
     *     figures agree with each other
     */
    @Transactional(readOnly = true)
    public long countLocked(Instant now) {
        return users.countByDeletedAtIsNullAndLockedUntilGreaterThan(now);
    }

    @Transactional(readOnly = true)
    public long countCreatedSince(Instant since) {
        return users.countByDeletedAtIsNullAndCreatedAtGreaterThanEqual(since);
    }

    /** Accounts that have signed in since a moment. Silent accounts are the complement. */
    @Transactional(readOnly = true)
    public long countSignedInSince(Instant since) {
        return users.countByDeletedAtIsNullAndLastLoginAtGreaterThanEqual(since);
    }
}
