package com.company.taskmanagementplatform.tasks;

/**
 * One authorized task somebody may add to, and whether they moderate what is already there.
 *
 * <p>Handed back by {@link TaskAccessGuard#requireContribution} to the {@code comments} and {@code
 * attachments} modules, which have the same two questions to ask and would otherwise each answer the
 * second one for themselves.
 *
 * <p>{@code moderator} is the write scope for somebody else's contribution: true when the caller
 * holds the relevant {@code manage_any} grant, or owns the task's project, or leads its team. It is
 * deliberately not "may delete this", because whether a caller wrote the thing is the other half of
 * that decision and only the owning module knows it.
 *
 * @param task the task, as {@link TaskRef} describes it
 * @param moderator the caller reaches other people's comments and files on this task
 */
public record TaskContribution(TaskRef task, boolean moderator) {}
