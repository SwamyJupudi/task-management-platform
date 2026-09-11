package com.company.taskmanagementplatform.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * When the deadline scan runs, how far ahead it looks, and how much it reads at a time.
 *
 * @param enabled whether the scheduled job runs at all. Off in the test profile, where the suite
 *     drives the scanner directly rather than racing a background job it did not start
 * @param cron when it runs. Daily rather than hourly: "deadline approaching" is a once-a-day fact,
 *     and an hourly scan would do twenty-four times the work to send the same row. Seven in the
 *     morning, so the message is waiting rather than arriving mid-afternoon
 * @param leadDays how many days ahead counts as approaching. Two, so somebody hears on a Monday
 *     about a Wednesday deadline and has a day to do something about it
 * @param batchSize how many tasks are read per page. The requirements say plainly not to fetch
 *     thousands of records unnecessarily, and the scan is the one query in the platform whose size
 *     grows with the whole estate rather than with one workspace
 */
@ConfigurationProperties(prefix = "app.notifications.deadline")
record DeadlineProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 7 * * *") String cron,
        @DefaultValue("2") int leadDays,
        @DefaultValue("500") int batchSize) {}
