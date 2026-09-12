package com.company.taskmanagementplatform.workspaces;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one workspace setting another module has to read: which timezone the company keeps.
 *
 * <p>It arrived with phase eight, which is the first thing in the platform that has to say what
 * "today" means. Overdue work, upcoming deadlines and the day a chart buckets by are all statements
 * about a local calendar, and a dashboard that called work overdue at midnight UTC would be wrong
 * for most of a company for most of the day.
 *
 * <p>A facade rather than a method on {@link WorkspaceAccessGuard}, because that class authorizes and
 * this answers a question of fact. The caller has already passed the guard by the time it gets here.
 *
 * <p><strong>An unreadable zone falls back to UTC rather than failing the request.</strong> The
 * column is free text with only a length check behind it, so a value that no longer names a real
 * zone is representable. Refusing to render a dashboard because a setting is malformed would take a
 * whole workspace's reporting away over one bad string; UTC and a WARN line naming the workspace
 * leave the figures slightly wrong and visibly so. Validating the column on write is recorded as
 * hardening work in {@code architecture.md}.
 */
@Service
public class WorkspaceSettingsFacade {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSettingsFacade.class);

    private final WorkspaceRepository workspaces;

    WorkspaceSettingsFacade(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    /**
     * The workspace's timezone, or UTC when it does not exist or cannot be read.
     *
     * <p>A missing workspace answers UTC rather than throwing. Every caller of this has already been
     * told by the workspace guard that the workspace exists, so a miss here means it went away
     * mid-request, and that is not a reason to return an error about timezones.
     */
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID workspaceId) {
        String configured = workspaces
                .findByIdAndDeletedAtIsNull(workspaceId)
                .map(Workspace::getTimezone)
                .orElse(null);

        if (configured == null || configured.isBlank()) {
            return ZoneId.of("UTC");
        }

        try {
            return ZoneId.of(configured.trim());
        } catch (DateTimeException e) {
            log.warn("Workspace {} has an unreadable timezone; reporting in UTC instead", workspaceId);
            return ZoneId.of("UTC");
        }
    }
}
