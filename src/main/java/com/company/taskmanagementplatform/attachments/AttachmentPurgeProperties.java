package com.company.taskmanagementplatform.attachments;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * When soft-deleted attachments have their stored objects reclaimed, how long after deletion, and how
 * many at a time.
 *
 * <p><strong>Off by default, and this is the job where that matters most.</strong> The token purge
 * removes rows that every lookup already refuses. This one destroys the only copy of somebody's file.
 * A deployment has to ask for it.
 *
 * @param enabled whether the scheduled purge runs at all. False by default, in every profile
 * @param cron when it runs. Deliberately not the token purge's half-hour and not the deadline scan's
 *     hour: the three do not contend for anything, but a night on which all three start at once is a
 *     harder one to read a log from
 * @param retention how long after an attachment was deleted its bytes are kept. Thirty days, matching
 *     the token purge. This is the number that decides whether a file deleted by mistake can be
 *     recovered: until the purge takes it the row is soft-deleted and the object is intact, so a
 *     restore is possible in principle for this long and impossible afterwards. Shortening it shortens
 *     that window
 * @param batchSize how many attachments one run considers at a time. Each one costs a call to the
 *     object store, so this is a bound on how long a single batch holds a connection rather than on a
 *     statement's cost
 */
@ConfigurationProperties(prefix = "app.storage.purge")
record AttachmentPurgeProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0 15 4 * * *") String cron,
        @DefaultValue("30d") Duration retention,
        @DefaultValue("200") int batchSize) {}
