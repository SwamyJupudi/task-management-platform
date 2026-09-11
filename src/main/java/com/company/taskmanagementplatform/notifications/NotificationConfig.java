package com.company.taskmanagementplatform.notifications;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The thread notifications are written on, and the first scheduler in the platform.
 *
 * <p>The executor is the phase-six pattern, copied deliberately rather than shared. Writing after
 * commit means a second transaction, and a committed transaction has not released its connection
 * when an after-commit listener runs, so writing on the request's own thread would make every
 * writing request hold two connections at once. Enough concurrent writers and the pool deadlocks;
 * that was found by {@code TaskNumberingConcurrencyIT} when the audit trail started listening, and
 * {@code ActivityConfig} tells the story in full.
 *
 * <p><strong>Its own executor rather than the activity one.</strong> Sharing a single thread between
 * the audit trail and the feed would let a slow recipient lookup delay an audit row, and audit rows
 * are the ones that must not be lost. Two queues, two failure domains.
 *
 * <p>One thread, on purpose, so the queue is FIFO end to end and two notifications caused by the
 * same action arrive in the order they happened. The queue is bounded: an unbounded one turns a
 * database that has stopped accepting writes into a memory leak that takes the application down with
 * it. It drains on shutdown, so an ordinary restart writes what it has.
 *
 * <p>What this costs, stated plainly: a notification queued when the process dies is lost. That is
 * the same bargain phase six made for audit rows, and it is a better one here, because a lost
 * notification is a message somebody did not get rather than a hole in a record.
 *
 * <p>{@link EnableScheduling} is on this class rather than on the application, so the platform's
 * first scheduled job arrives with the feature that needs it and nothing else starts running in the
 * background by accident.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(DeadlineProperties.class)
class NotificationConfig {

    static final String EXECUTOR = "notificationExecutor";

    @Bean(name = EXECUTOR)
    ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10_000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
