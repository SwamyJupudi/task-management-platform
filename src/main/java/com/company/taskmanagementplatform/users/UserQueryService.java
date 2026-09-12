package com.company.taskmanagementplatform.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads over the account directory, for the administrative views.
 *
 * <p>Separate from {@link UserAccountService} because the two have different shapes: this one only
 * ever reads, always pages, and never publishes an event. Keeping them apart also keeps the lifecycle
 * service from growing a second personality.
 */
@Service
public class UserQueryService {

    private final UserRepository users;
    private final java.time.Clock clock;

    UserQueryService(UserRepository users, java.time.Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /**
     * A page of accounts, optionally narrowed by a search term or a status.
     *
     * <p>Paged rather than listed. The requirements are explicit that a query must not fetch
     * thousands of rows it does not need, and an unbounded user list is the first place that goes
     * wrong.
     *
     * @param search matched against address, first name and last name; null means no narrowing
     * @param status exact status to filter by, or null for all
     */
    @Transactional(readOnly = true)
    public Page<UserAccount> search(String search, UserStatus status, Pageable pageable) {
        return search(search, status, false, pageable);
    }

    /**
     * The same directory, optionally narrowed to accounts that are locked out right now.
     *
     * <p>"Locked" is a live lockout rather than one that has since expired, so it is a different
     * question from {@code DEACTIVATED} and the two combine rather than compete. An administrator
     * looking for "who cannot sign in and does not know why" wants this one.
     *
     * <p>Four repository methods rather than one with nullable parameters, following the reasoning
     * already written on {@code UserRepository}: a bare null in a typed position gives PostgreSQL
     * nothing to infer a type from.
     */
    @Transactional(readOnly = true)
    public Page<UserAccount> search(String search, UserStatus status, boolean lockedOnly, Pageable pageable) {
        // An absent term becomes a pattern that matches everything, so the query
        // never has to carry a null it cannot type.
        String pattern = search == null || search.isBlank()
                ? "%"
                : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";

        java.time.Instant now = clock.instant();

        Page<User> page;
        if (lockedOnly) {
            page = status == null
                    ? users.searchLocked(pattern, now, pageable)
                    : users.searchLockedByStatus(pattern, status, now, pageable);
        } else {
            page = status == null
                    ? users.search(pattern, pageable)
                    : users.searchByStatus(pattern, status, pageable);
        }

        return page.map(UserAccountService::toAccount);
    }
}
