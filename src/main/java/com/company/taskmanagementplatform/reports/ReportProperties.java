package com.company.taskmanagementplatform.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much a report is allowed to ask for, and how far ahead a dashboard looks.
 *
 * <p>Every value here is a bound rather than a preference. Nothing in this phase is cached, so each
 * figure is computed at request time over whatever range the caller names, and the requirements say
 * plainly not to fetch thousands of records unnecessarily. These are where that instruction is
 * spelled as numbers.
 *
 * @param defaultPeriodDays the window used when a request names neither end of one. A month, because
 *     that is the stretch a productivity question is usually asked about
 * @param maxPeriodDays the widest window accepted. A year and a day, so a request for "the last
 *     twelve months" inclusive of both ends is not refused by one
 * @param upcomingLeadDays how far ahead the employee dashboard looks for deadlines. Seven, and
 *     deliberately not {@code app.notifications.deadline.lead-days}, which is two: a notification is
 *     a nudge about something imminent, a dashboard is a week's planning
 * @param maxPageSize the cap on a report listing. A page over it is refused rather than silently
 *     clamped, because a clamped page makes a client's own paging arithmetic wrong
 */
@ConfigurationProperties(prefix = "app.reports")
record ReportProperties(
        @DefaultValue("30") int defaultPeriodDays,
        @DefaultValue("366") int maxPeriodDays,
        @DefaultValue("7") int upcomingLeadDays,
        @DefaultValue("100") int maxPageSize) {}
