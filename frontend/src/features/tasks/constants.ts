import type { TaskPriority, TaskStatus } from './types'

/**
 * The lifecycle, the priorities, and what a client is allowed to sort by.
 *
 * These mirror enums and an allowlist the backend owns but does not publish:
 * no endpoint returns the state machine or the sortable fields, so the
 * interface has to know them to offer sensible controls. Every one is still
 * checked server-side — an illegal transition answers 409 and an unknown sort
 * field answers 400 — so this is about not offering a move that will fail,
 * never about deciding whether it is allowed.
 *
 * Keep in step with `TaskStatus.allowedTargets`, `TaskPriority` and
 * `TaskQuery.SORTABLE`.
 */

/** Board order: the order the columns read left to right. */
export const TASK_STATUSES: readonly TaskStatus[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE']

export const STATUS_LABELS: Readonly<Record<TaskStatus, string>> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  REVIEW: 'Review',
  DONE: 'Done',
}

/**
 * Where each state may go next, copied from `TaskStatus.allowedTargets`.
 *
 * Note that `DONE` can be reopened into any earlier state, and that `TODO`
 * can jump straight to `DONE` — work that turns out to need nothing is
 * finished, not marched through review first.
 */
export const ALLOWED_TRANSITIONS: Readonly<Record<TaskStatus, readonly TaskStatus[]>> = {
  TODO: ['IN_PROGRESS', 'DONE'],
  IN_PROGRESS: ['TODO', 'REVIEW', 'DONE'],
  REVIEW: ['IN_PROGRESS', 'DONE'],
  DONE: ['TODO', 'IN_PROGRESS', 'REVIEW'],
}

export const TASK_PRIORITIES: readonly TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export const PRIORITY_LABELS: Readonly<Record<TaskPriority, string>> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
}

/** The sort fields `TaskQuery.SORTABLE` accepts, with readable names. */
export const SORT_OPTIONS: readonly { value: string; label: string }[] = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'dueDate,asc', label: 'Due soonest' },
  { value: 'dueDate,desc', label: 'Due latest' },
  { value: 'priority,desc', label: 'Priority' },
  { value: 'status,asc', label: 'Status' },
  { value: 'title,asc', label: 'Title A to Z' },
  { value: 'taskNumber,desc', label: 'Task number' },
]

export const DEFAULT_SORT = 'createdAt,desc'
export const DEFAULT_PAGE_SIZE = 20

/** Guards a value that arrived from a URL before it is used as a filter. */
export function isTaskStatus(value: string): value is TaskStatus {
  return (TASK_STATUSES as readonly string[]).includes(value)
}

export function isTaskPriority(value: string): value is TaskPriority {
  return (TASK_PRIORITIES as readonly string[]).includes(value)
}

/**
 * Re-exported from `@/lib/datetime`, where it now lives.
 *
 * The workload report writes the same effort figures, so there is one
 * implementation and two features reading it rather than two that drift.
 */
export { formatMinutes } from '@/lib/datetime'
