package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * That two instances do not both scan.
 *
 * <p>The lock is the interesting half of this and the dedupe key is the safety net. They are tested
 * separately on purpose: if only the outcome were asserted, the unique index would make a completely
 * broken lock look like a working one, because the duplicate rows would be refused anyway and the
 * count would still come out right.
 *
 * <p>The first two tests therefore hold the lock from outside the application, on a connection of
 * their own, and assert that the scanner does nothing at all rather than that it writes nothing.
 */
class DeadlineSchedulerLockIT extends NotificationTestBase {

    /** The same constant {@code DeadlineScanner} uses. A test that guessed would prove nothing. */
    private static final long LOCK_KEY = 8_421_337_001L;

    @Autowired
    private DeadlineScanner scanner;

    @Autowired
    private DataSource dataSource;

    @Test
    void anInstanceThatCannotTakeTheLockDoesNotScan() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due tomorrow", LocalDate.now().plusDays(1), assignee);

        try (Connection held = dataSource.getConnection()) {
            assertThat(takeLock(held)).isTrue();

            // Another instance is holding it, so this one goes back to sleep.
            assertThat(scanner.run()).isZero();
            assertThat(countFor(assignee, "task.deadline_approaching")).isZero();
        }
    }

    @Test
    void theLockIsReleasedWhenTheScanFinishes() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due tomorrow", LocalDate.now().plusDays(1), assignee);

        scanner.run();
        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(1);

        // Taking it from outside now proves the scan gave it back. Acquire and
        // release happen on one connection, so a leak here would be permanent until
        // that connection happened to be closed.
        try (Connection after = dataSource.getConnection()) {
            assertThat(takeLock(after)).isTrue();
            releaseLock(after);
        }
    }

    @Test
    void twoConcurrentRunsProduceOneSetOfRows() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Contended", LocalDate.now().plusDays(1), assignee);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> run = () -> scanner.run();
            List<Future<Integer>> results = pool.invokeAll(List.of(run, run));

            for (Future<Integer> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(1);
    }

    private boolean takeLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private void releaseLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.executeQuery().close();
        }
    }
}
