import type { ProjectPriority, ProjectStatus } from './types'

/**
 * The lifecycle, the priorities, and what a client is allowed to sort by.
 *
 * These mirror enums and an allowlist the backend owns but does not publish:
 * no endpoint returns the state machine or the sortable fields, so the
 * interface has to know them to offer sensible controls. Every one of them is
 * still checked server-side — an illegal transition answers 409 and an unknown
 * sort field answers 400 — so this is about not offering a move that will fail,
 * never about deciding whether it is allowed.
 *
 * Keep in step with `ProjectStatus.allowedTargets`, `ProjectPriority` and
 * `ProjectQuery.SORTABLE`.
 */

/** Board order, which is also the order a project usually moves through. */
export const PROJECT_STATUSES: readonly ProjectStatus[] = [
  'PLANNING',
  'ACTIVE',
  'ON_HOLD',
  'COMPLETED',
  'ARCHIVED',
]

export const STATUS_LABELS: Readonly<Record<ProjectStatus, string>> = {
  PLANNING: 'Planning',
  ACTIVE: 'Active',
  ON_HOLD: 'On hold',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
}

/**
 * Where each state may go next, copied from `ProjectStatus.allowedTargets`.
 *
 * Archiving is reachable from everywhere, and an archived project can be
 * brought back to any state, which is what makes archiving a reversible shelf
 * rather than a deletion.
 */
export const ALLOWED_TRANSITIONS: Readonly<Record<ProjectStatus, readonly ProjectStatus[]>> = {
  PLANNING: ['ACTIVE', 'ARCHIVED'],
  ACTIVE: ['ON_HOLD', 'COMPLETED', 'ARCHIVED'],
  ON_HOLD: ['ACTIVE', 'ARCHIVED'],
  COMPLETED: ['ACTIVE', 'ARCHIVED'],
  ARCHIVED: ['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED'],
}

export const PROJECT_PRIORITIES: readonly ProjectPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export const PRIORITY_LABELS: Readonly<Record<ProjectPriority, string>> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
}

/** The sort fields `ProjectQuery.SORTABLE` accepts, with readable names. */
export const SORT_OPTIONS: readonly { value: string; label: string }[] = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'name,asc', label: 'Name A to Z' },
  { value: 'name,desc', label: 'Name Z to A' },
  { value: 'key,asc', label: 'Key' },
  { value: 'priority,desc', label: 'Priority' },
  { value: 'status,asc', label: 'Status' },
  { value: 'endDate,asc', label: 'Ending soonest' },
  { value: 'startDate,desc', label: 'Started most recently' },
]

export const DEFAULT_SORT = 'createdAt,desc'
export const DEFAULT_PAGE_SIZE = 20

/** Guards a value that arrived from a URL before it is used as a filter. */
export function isProjectStatus(value: string): value is ProjectStatus {
  return (PROJECT_STATUSES as readonly string[]).includes(value)
}

export function isProjectPriority(value: string): value is ProjectPriority {
  return (PROJECT_PRIORITIES as readonly string[]).includes(value)
}
