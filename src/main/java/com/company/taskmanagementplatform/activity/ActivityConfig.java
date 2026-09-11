package com.company.taskmanagementplatform.activity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The thread the audit trail is written on, and the reason it is not the request's own.
 *
 * <p>Writing after commit means opening a second transaction, and a second transaction means a
 * second connection. The catch is that the committed transaction has not released its own yet when
 * an after-commit listener runs, so every writing request would hold two connections at once. With
 * more concurrent writers than half the pool, every one of them holds one and waits for another, and
 * the pool deadlocks until Hikari's timeout expires. That is not a theoretical shape: it was found
 * by {@code TaskNumberingConcurrencyIT}, a phase-five test that creates twelve tasks at once against
 * a pool of ten, failing the moment this module started listening.
 *
 * <p>Handing the write to this executor breaks the cycle. The request finishes, its connection goes
 * back to the pool, and the audit row is written a moment later on a thread that competes with
 * nobody.
 *
 * <p><strong>One thread, on purpose.</strong> The queue is then FIFO end to end, so rows are written
 * in the order the events happened. A second thread would buy throughput this workload does not need
 * and would make two rows written in the same millisecond arrive in either order.
 *
 * <p>The queue is bounded. An unbounded one turns a database that has stopped accepting writes into
 * a memory leak that takes the application down with it; a bounded one drops rows and says so. The
 * executor waits for its queue to drain on shutdown, so an ordinary restart writes what it has
 * rather than discarding it.
 *
 * <p>What this costs, stated plainly: a row that is queued when the process dies is lost. The
 * approved design already accepts losing a row whose write fails, and this widens that window rather
 * than opening a new kind of hole. The alternative, writing inside the caller's transaction, would
 * make an audit failure roll back somebody's work.
 */
@Configuration
class ActivityConfig {

    static final String EXECUTOR = "activityExecutor";

    @Bean(name = EXECUTOR)
    ThreadPoolTaskExecutor activityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("activity-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10_000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
