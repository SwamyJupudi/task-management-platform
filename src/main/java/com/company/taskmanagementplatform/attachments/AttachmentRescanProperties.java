package com.company.taskmanagementplatform.attachments;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * When the unscanned backlog is worked through, and how much of it at a time.
 *
 * <p>This job exists because of a decision in {@code V12__attachment_scan_state.sql}: every attachment that
 * predates malware scanning is recorded as {@code PENDING} rather than being backfilled to {@code CLEAN},
 * because nothing had inspected those files and the column exists to say whether anything had. The
 * consequence is that upgrading a populated installation makes its existing attachments undownloadable, and
 * this is what makes that recoverable rather than permanent.
 *
 * <p><strong>Off by default, like the two purges, and for a related but not identical reason.</strong> Those
 * destroy data. This one does not, but it reads every unscanned file out of object storage and sends it to a
 * scanner, which on an installation with a large backlog is a substantial amount of egress and scanner load
 * arriving the moment a deployment restarts. A deployment should switch it on when it is ready to pay for
 * that, and should expect the backlog to take as long as the scanner needs.
 *
 * <p>There is deliberately no "mark everything clean" shortcut anywhere in the application. A file becomes
 * downloadable when a scanner has looked at it and not before.
 *
 * @param enabled whether the scheduled rescan runs at all. False by default, in every profile
 * @param cron when it runs. Hourly rather than nightly, unlike the purges: a backlog is a thing an operator
 *     is waiting on, and a deployment that has just upgraded wants it to make visible progress rather than one
 *     batch a day. Once the backlog is empty the job costs one indexed query per hour that returns nothing
 * @param batchSize how many attachments one run considers. Each one costs a read from the object store, the
 *     whole file held in memory, and a scanner call, so this bounds the memory and the wall-clock of a single
 *     run rather than a statement's cost. Deliberately small for that reason
 */
@ConfigurationProperties(prefix = "app.storage.rescan")
record AttachmentRescanProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0 20 * * * *") String cron,
        @DefaultValue("50") int batchSize) {}
