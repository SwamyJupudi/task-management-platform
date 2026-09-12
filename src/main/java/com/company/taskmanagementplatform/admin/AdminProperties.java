package com.company.taskmanagementplatform.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much the admin panel is allowed to ask for.
 *
 * <p>Every value here is a bound rather than a preference, exactly as {@code ReportProperties} is.
 * Nothing in this phase is cached either, and these are the platform's first queries with no tenant
 * predicate at all, so the requirements' instruction about not fetching records unnecessarily
 * matters more here than anywhere else. These are where it is spelled as numbers.
 *
 * @param statsWindowDays the trailing window the "recent" figures use when a request names none. A
 *     month, because that is the stretch "how busy have we been" is usually asked about
 * @param maxStatsWindowDays the widest window accepted. A year and a day, matching the report cap,
 *     so the two administrative surfaces do not disagree about what "too far back" means
 * @param maxPageSize the cap on an admin listing. A page over it is refused rather than silently
 *     clamped, because a clamped page makes a client's own paging arithmetic wrong
 */
@ConfigurationProperties(prefix = "app.admin")
record AdminProperties(
        @DefaultValue("30") int statsWindowDays,
        @DefaultValue("366") int maxStatsWindowDays,
        @DefaultValue("100") int maxPageSize) {}
