package com.company.taskmanagementplatform.notifications;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One instance at a time, using a lock PostgreSQL already has.
 *
 * <p>The deadline scan runs on every instance of the application, because a cron expression is a
 * property of the process rather than of the estate. Two instances scanning at once would be
 * harmless in the end, since the unique index on {@code dedupe_key} refuses the second copy of every
 * row, but it would be twice the reading for nothing and the losing instance would spend its morning
 * colliding with the winner. This makes the second instance find out immediately and go back to
 * sleep.
 *
 * <p><strong>Acquire and release on the same connection, and that is the whole design.</strong> A
 * session-level advisory lock belongs to the connection that took it. Taking it through a pooled
 * {@code JdbcTemplate} call and releasing it through another would almost always release a lock the
 * second connection never held, which PostgreSQL answers with a warning and a {@code false} rather
 * than an error, leaving the real lock held until that connection happened to be closed. So one
 * connection is opened here, held for the whole run, and closed at the end. Closing it is itself a
 * release: PostgreSQL drops every session lock when the session ends, which is the backstop if the
 * explicit unlock cannot run.
 *
 * <p>The cost is one connection held for the duration of the scan. That is acceptable because the
 * scan runs once a day on a thread that competes with no request, and it is the reason the lock is
 * not simply taken inside the scanning transaction: the scan is many transactions, one per page.
 *
 * <p>No new dependency. A scheduler-locking library would do this and manage a table for it; this is
 * one statement and a connection, and the requirements ask for no more.
 */
@Component
class AdvisoryLock {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryLock.class);

    private final DataSource dataSource;

    AdvisoryLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs the work if this instance gets the lock, and does nothing at all if it does not.
     *
     * @param key the lock identifier, shared by every instance that must not overlap
     * @return whether the work ran here
     */
    boolean runExclusively(long key, Runnable work) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection, key)) {
                log.debug("Another instance holds the lock {}, skipping this run", key);
                return false;
            }

            try {
                work.run();
            } finally {
                unlock(connection, key);
            }
            return true;

        } catch (SQLException e) {
            throw new IllegalStateException("Could not take the advisory lock " + key, e);
        }
    }

    private boolean tryLock(Connection connection, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    /**
     * Releases it, and never lets the release hide what the work threw.
     *
     * <p>A failure here is logged rather than raised, because this runs in a {@code finally} and an
     * exception from it would replace the real one. Closing the connection releases the lock anyway.
     */
    private void unlock(Connection connection, long key) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && !result.getBoolean(1)) {
                    log.warn("Advisory lock {} was not held by this connection at release", key);
                }
            }
        } catch (SQLException e) {
            log.warn("Could not release the advisory lock {}; closing the connection will release it", key, e);
        }
    }
}
