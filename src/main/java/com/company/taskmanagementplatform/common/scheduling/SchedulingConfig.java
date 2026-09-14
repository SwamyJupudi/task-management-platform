package com.company.taskmanagementplatform.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns the scheduler on, once, for the whole application.
 *
 * <p>{@link EnableScheduling} sat on {@code NotificationConfig} while the deadline scan was the only
 * scheduled job, with the stated reason that scheduling should arrive with the feature needing it and
 * that nothing else should start running in the background by accident. The hardening phase added two
 * more jobs, in two other modules, and left that annotation describing something untrue: it was no
 * longer notifications that needed the scheduler, it was the application.
 *
 * <p>Moving it here keeps the original intent — there is exactly one place that switches background
 * work on, and it is findable — while removing the accident that any module adding a {@code @Scheduled}
 * method was silently relying on the notifications package continuing to exist.
 *
 * <p><strong>Every job this enables is off by default except the deadline scan.</strong> The two
 * purges delete data, so each is behind its own {@code enabled} flag defaulting to false: a deployment
 * opts into destruction rather than discovering it. The deadline scan sends a message and creates
 * nothing that cannot be dismissed, which is why it is the one that defaults on.
 *
 * <p>The pool size is set in {@code application.properties} rather than here. Spring's default
 * scheduler is a single thread, and a purge that ran long would postpone the deadline scan behind it;
 * the property says how many jobs may overlap and why.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
