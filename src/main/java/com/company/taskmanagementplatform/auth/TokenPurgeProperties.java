package com.company.taskmanagementplatform.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * When expired tokens are deleted, how long after expiry, and how many at a time.
 *
 * <p><strong>Off by default, unlike the deadline scan.</strong> That difference is deliberate and it
 * is the only safe way round. The scan sends a message; this deletes rows. A job that destroys data
 * should be something a deployment switched on, not something it discovered had been running.
 *
 * @param enabled whether the scheduled purge runs at all. False by default, in every profile
 * @param cron when it runs. Early morning, and deliberately not the deadline scan's hour: the two
 *     compete for nothing, but a purge running alongside the one job that has to finish on time is a
 *     needless coincidence
 * @param retention how long after a token has <em>expired</em> its row is kept. Thirty days, matching
 *     the attachment purge, because the two are the same kind of open item and one retention figure is
 *     easier to reason about than two. The row is useless to an attacker and to the application the
 *     moment it expires — every lookup checks expiry — so the retention buys nothing but the ability
 *     to answer "what happened to my reset link last week" from the database rather than from the logs
 * @param batchSize how many rows one statement deletes. The purge is bounded rather than unbounded for
 *     the reason the deadline scan is paged: the row count grows with the whole installation and with
 *     time, so the first run after this is switched on is the largest one there will ever be, and a
 *     single unbounded {@code DELETE} would hold a lock on the busiest table in the schema for as long
 *     as it took
 */
@ConfigurationProperties(prefix = "app.auth.token-purge")
record TokenPurgeProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0 30 3 * * *") String cron,
        @DefaultValue("30d") Duration retention,
        @DefaultValue("500") int batchSize) {}
