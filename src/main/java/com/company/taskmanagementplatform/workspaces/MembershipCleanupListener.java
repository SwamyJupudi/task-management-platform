package com.company.taskmanagementplatform.workspaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.users.UserDeletedEvent;

/**
 * Takes a removed account off every workspace roster.
 *
 * <p>Without this the membership rows outlive the person, and a roster renders them as a row with no
 * address and no name, because the account behind it is filtered out of every read. Filtering the
 * ghost out at read time instead was the alternative and is worse: it would make a page of twenty
 * sometimes return nineteen, and every future query over members would have to remember the same
 * rule.
 *
 * <p>The rows are deleted rather than flagged, which follows the convention in {@code database.md}
 * that soft deletion never applies to a join table. The record of who was in a workspace and when is
 * the audit log's job, and that arrives in phase six.
 *
 * <p>Runs inside the publishing transaction, so the removal and the cleanup succeed or fail
 * together.
 */
@Component
class MembershipCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(MembershipCleanupListener.class);

    private final WorkspaceMemberRepository members;

    MembershipCleanupListener(WorkspaceMemberRepository members) {
        this.members = members;
    }

    @EventListener
    @Transactional
    void onUserDeleted(UserDeletedEvent event) {
        long removed = members.deleteAllByUserId(event.userId());
        if (removed > 0) {
            log.info("Removed {} workspace membership(s) for a deleted account: userId={}", removed, event.userId());
        }
    }
}
