package com.company.taskmanagementplatform.tasks;

import java.time.LocalDate;

/**
 * One bucket of a trend: the day, week or month it starts, and how many tasks fall in it.
 *
 * <p>The bucket is a local date in the workspace's own timezone, not in UTC. A company whose day
 * ends at midnight local would otherwise see work done on a Friday evening counted against
 * Saturday, and a trend that disagrees with the calendar on the wall is worse than no trend.
 *
 * <p>Buckets with nothing in them are absent here and filled in by the reports module, which is the
 * one that knows a chart needs a flat line rather than a gap.
 */
public record TaskTrendPoint(LocalDate bucketStart, long count) {}
