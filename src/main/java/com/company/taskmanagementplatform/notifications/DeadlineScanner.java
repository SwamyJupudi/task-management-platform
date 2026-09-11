package com.company.taskmanagementplatform.notifications;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.notifications.NotificationWriter.NotificationRow;
import com.company.taskmanagementplatform.tasks.TaskDigest;
import com.company.taskmanagementplatform.tasks.TaskNotificationFacade;

/**
 * The one trigger nobody performs.
 *
 * <p>Five of the six the requirements name are things a person did, and they arrive as events. "Task
 * deadline approaching" is the exception: nothing happens, time passes, and somebody should be told.
 * That is why this class exists and why it is the only scheduled job in the platform.
 *
 * <p><strong>Daily, not hourly.</strong> An approaching deadline is a once-a-day fact. An hourly
 * scan would read the same tasks twenty-four times to send the same rows, and the unique index would
 * throw away twenty-three of the attempts.
 *
 * <p><strong>Idempotent by construction.</strong> Every row carries a dedupe key of the task and the
 * due date it was sent for, and the partial unique index refuses a second one. A re-run after a
 * crash, an overlapping run, and a manual invocation all write nothing the second time. Nothing here
 * asks the database what it has already sent; asking would race with the writing anyway.
 *
 * <p><strong>Paged.</strong> This is the only query in the platform whose size grows with the whole
 * estate rather than with one workspace, and the requirements say plainly not to fetch thousands of
 * records unnecessarily. It walks fixed-size pages and writes a batch per page, so memory is bounded
 * by the page size rather than by how much work is due on a Monday.
 */
@Component
class DeadlineScanner {

    private static final Logger log = LoggerFactory.getLogger(DeadlineScanner.class);

    /**
     * The advisory lock every instance competes for.
     *
     * <p>An arbitrary constant, and it only has to be unique among the advisory locks this
     * application takes. It is the only one so far; a second one belongs beside it here rather than
     * invented at a call site.
     */
    private static final long LOCK_KEY = 8_421_337_001L;

    private final TaskNotificationFacade tasks;
    private final NotificationWriter writer;
    private final AdvisoryLock lock;
    private final DeadlineProperties properties;
    private final Clock clock;

    DeadlineScanner(
            TaskNotificationFacade tasks,
            NotificationWriter writer,
            AdvisoryLock lock,
            DeadlineProperties properties,
            Clock clock) {
        this.tasks = tasks;
        this.writer = writer;
        this.lock = lock;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The scheduled entry point, which does nothing when the job is switched off.
     *
     * <p>The check is here rather than on the bean, because the tests need the scanner to exist so
     * they can drive it directly. A test that waited for seven in the morning would not be a test.
     */
    @Scheduled(cron = "${app.notifications.deadline.cron}")
    void scheduled() {
        if (!properties.enabled()) {
            return;
        }

        try {
            run();
        } catch (RuntimeException e) {
            // Nothing above this catches: a scheduled method that throws is logged by the
            // framework and silently not retried, and this says which job it was.
            log.error("The deadline scan failed", e);
        }
    }

    /**
     * Scans once, if this instance gets the lock.
     *
     * @return how many notifications were written, and zero when another instance is running it
     */
    int run() {
        DeadlineWindow window = DeadlineWindow.ahead(LocalDate.now(clock), properties.leadDays());
        AtomicInteger written = new AtomicInteger();

        boolean ran = lock.runExclusively(LOCK_KEY, () -> written.set(scan(window)));
        if (!ran) {
            return 0;
        }

        log.info(
                "Deadline scan complete: from={} to={} written={}",
                window.from(),
                window.to(),
                written.get());
        return written.get();
    }

    private int scan(DeadlineWindow window) {
        Instant now = clock.instant();
        int written = 0;
        int pageNumber = 0;

        while (true) {
            Page<TaskDigest> page = tasks.findDueBetween(
                    window.from(), window.to(), PageRequest.of(pageNumber, properties.batchSize()));

            if (page.isEmpty()) {
                return written;
            }

            written += writer.writeAll(page.getContent().stream()
                    .map(task -> row(task, window))
                    .toList(), now);

            if (!page.hasNext()) {
                return written;
            }
            pageNumber++;
        }
    }

    /**
     * One task's row.
     *
     * <p>No actor, because nobody did this. Inventing a system user to fill the column would put a
     * fictional person in somebody's feed, which is the same reasoning the audit trail applies to its
     * own nullable actor.
     */
    private NotificationRow row(TaskDigest task, DeadlineWindow window) {
        return new NotificationRow(
                task.workspaceId(),
                task.assigneeUserId(),
                null,
                NotificationType.TASK_DEADLINE_APPROACHING,
                task.taskId(),
                task.projectId(),
                Map.of(
                        "dueDate", task.dueDate().toString(),
                        "daysRemaining", window.daysRemaining(task.dueDate())),
                DeadlineWindow.dedupeKey(task.taskId(), task.dueDate()));
    }
}
