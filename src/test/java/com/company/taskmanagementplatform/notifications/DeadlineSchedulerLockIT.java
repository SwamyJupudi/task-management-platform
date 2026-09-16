package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
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
 *
 * <h2>The other instance is a real second session, not a pooled connection</h2>
 *
 * <p>This is the whole reason {@link #otherInstance()} exists, and it is worth reading before
 * changing it back to {@code dataSource.getConnection()}.
 *
 * <p>A session-level advisory lock belongs to the PostgreSQL <em>session</em>, and it is released
 * when that session ends or when the holder unlocks it. Closing a connection borrowed from Hikari
 * does neither: it returns the connection to the pool, where the session stays open and keeps every
 * advisory lock it was holding. A test that took the lock through the pool and did not give it back
 * would leave it held by an idle pooled connection for the rest of the suite, and the next test to
 * ask the scanner to run would find the lock taken, scan nothing, and fail — or not, depending
 * entirely on which connection the pool happened to hand out. That is a test that fails on one
 * machine and passes on another, which is the worst kind.
 *
 * <p>So the lock these tests hold is taken on a connection opened straight to the container, it is
 * released explicitly, and closing it ends the session as a backstop. {@link
 * #theLockIsNeverLeftHeld()} then asserts after every test that nobody is holding the key, so a
 * future leak fails immediately and in the test that caused it rather than somewhere else.
 */
class DeadlineSchedulerLockIT extends NotificationTestBase {

    /** The same constant {@code DeadlineScanner} uses. A test that guessed would prove nothing. */
    private static final long LOCK_KEY = 8_421_337_001L;

    @Autowired
    private DeadlineScanner scanner;

    /**
     * Nobody holds the key once a test is over — not the application, and not the test.
     *
     * <p>The scanner releases in a {@code finally} and these tests release what they take, so this
     * should never fire. It exists because the failure it detects does not show up here: it shows up
     * later, in a different test, as a scan that mysteriously did nothing.
     */
    @AfterEach
    void theLockIsNeverLeftHeld() {
        assertThat(holdersOfTheLockKey())
                .as("advisory lock %s was still held after the test", LOCK_KEY)
                .isZero();
    }

    @Test
    void anInstanceThatCannotTakeTheLockDoesNotScan() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Due tomorrow", LocalDate.now().plusDays(1), assignee);

        try (Connection held = otherInstance()) {
            assertThat(takeLock(held)).isTrue();
            try {
                // Another instance is holding it, so this one goes back to sleep.
                assertThat(scanner.run()).isZero();
                assertThat(countFor(assignee, "task.deadline_approaching")).isZero();
            } finally {
                // In a finally, so a failed assertion above does not also leave the
                // lock held and turn one failure into several.
                releaseLock(held);
            }
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
        try (Connection after = otherInstance()) {
            assertThat(takeLock(after)).isTrue();
            releaseLock(after);
        }
    }

    @Test
    void twoConcurrentRunsProduceOneSetOfRows() throws Exception {
        Scene scene = scene();
        UUID assignee = settledProjectMember(scene, "EMPLOYEE");
        taskDueOn(scene, "Contended", LocalDate.now().plusDays(1), assignee);

        // A barrier, so the two runs genuinely overlap rather than happening to.
        // Without it a fast first run can finish before the second starts, and the
        // test would still pass while proving nothing about contention.
        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        int wroteSomething = 0;
        try {
            Callable<Integer> run = () -> {
                bothReady.await(30, TimeUnit.SECONDS);
                return scanner.run();
            };
            List<Future<Integer>> results = pool.invokeAll(List.of(run, run));

            for (Future<Integer> result : results) {
                if (result.get(60, TimeUnit.SECONDS) > 0) {
                    wroteSomething++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // One set of rows, which is the requirement.
        assertThat(countFor(assignee, "task.deadline_approaching")).isEqualTo(1);

        // And it was the lock that produced that, not the dedupe index quietly
        // absorbing a second scan. Exactly one of the two runs did any work: the
        // winner wrote at least this task's row, and the loser either never got the
        // lock or got it afterwards and found everything already written.
        assertThat(wroteSomething)
                .as("exactly one of the two concurrent runs should have written anything")
                .isEqualTo(1);
    }

    /**
     * A connection of its own, to the container, standing in for a second instance of the
     * application.
     *
     * <p>Not {@code dataSource.getConnection()}: see the note on this class. Closing this one really
     * does end the session, so a lock it holds cannot outlive the test whatever happens.
     */
    private Connection otherInstance() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** How many sessions hold the deadline key, read from {@code pg_locks}. */
    private int holdersOfTheLockKey() {
        // An advisory key is stored split across two columns: the high 32 bits in
        // classid and the low 32 in objid. This puts them back together rather than
        // counting every advisory lock in the cluster, so a lock another job holds
        // cannot fail this.
        Integer held = jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_locks
                WHERE locktype = 'advisory'
                  AND ((classid::bigint << 32) | objid::bigint) = ?
                """,
                Integer.class,
                LOCK_KEY);
        return held == null ? 0 : held;
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
