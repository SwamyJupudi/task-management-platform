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

    UserQueryService(UserRepository users) {
        this.users = users;
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
        // An absent term becomes a pattern that matches everything, so the query
        // never has to carry a null it cannot type.
        String pattern = search == null || search.isBlank()
                ? "%"
                : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";

        Page<User> page = status == null
                ? users.search(pattern, pageable)
                : users.searchByStatus(pattern, status, pageable);

        return page.map(UserAccountService::toAccount);
    }
}
