package com.company.taskmanagementplatform.teams;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.users.UserDeletedEvent;
import com.company.taskmanagementplatform.workspaces.WorkspaceMemberRemovedEvent;

/**
 * Stands the teams module down when the person underneath it goes away.
 *
 * <p>Two things depend on a workspace membership here, and both are foreign keys into {@code
 * workspace_members}: the rows saying this person is in a team, and the column saying they lead one.
 * Until both are gone, PostgreSQL refuses to delete the membership. So this is not tidying that can
 * be deferred to a sweep; it is the step that makes the removal possible at all.
 *
 * <p>Ordered first for that reason, ahead of the listener in {@code workspaces} that deletes the
 * membership row itself. The flush is the other half: Hibernate may order the statements in a flush
 * by entity type rather than by call order, so the deletes are forced out before the publisher
 * continues.
 *
 * <p>Runs inside the publishing transaction, so the removal and the cleanup succeed or fail
 * together. Team rows are deleted rather than flagged, following the convention that soft deletion
 * never applies to a join table. Leadership is cleared rather than reassigned, because choosing
 * somebody's replacement is not a decision this code is in a position to make.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TeamCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(TeamCleanupListener.class);

    private final TeamRepository teams;
    private final TeamMemberRepository members;

    TeamCleanupListener(TeamRepository teams, TeamMemberRepository members) {
        this.teams = teams;
        this.members = members;
    }

    @EventListener
    @Transactional
    void onWorkspaceMemberRemoved(WorkspaceMemberRemovedEvent event) {
        List<Team> led = teams.findAllByWorkspaceIdAndLeadUserId(event.workspaceId(), event.userId());
        led.forEach(Team::clearLead);

        long removed = members.deleteAllByWorkspaceIdAndUserId(event.workspaceId(), event.userId());
        members.flush();

        if (removed > 0 || !led.isEmpty()) {
            log.info(
                    "Cleared team state for a removed workspace member: workspaceId={} userId={} teams={} led={}",
                    event.workspaceId(),
                    event.userId(),
                    removed,
                    led.size());
        }
    }

    /**
     * The same cleanup, but across every workspace at once.
     *
     * <p>A deleted account may have led teams in several workspaces, and the membership listener in
     * {@code workspaces} removes all of its rosters in one statement, so nothing narrower would be
     * enough.
     */
    @EventListener
    @Transactional
    void onUserDeleted(UserDeletedEvent event) {
        UUID userId = event.userId();

        List<Team> led = teams.findAllByLeadUserId(userId);
        led.forEach(Team::clearLead);

        long removed = members.deleteAllByUserId(userId);
        members.flush();

        if (removed > 0 || !led.isEmpty()) {
            log.info(
                    "Cleared team state for a deleted account: userId={} teams={} led={}",
                    userId,
                    removed,
                    led.size());
        }
    }
}
