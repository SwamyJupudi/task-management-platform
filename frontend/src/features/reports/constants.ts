import type { Granularity } from './types'

/**
 * The sort allowlists, the buckets and the caps the backend already enforces.
 *
 * Copied from `ReportSorts`, `ReportProperties` and `TrendGranularity` because
 * no endpoint publishes them. Every one is checked server-side — an unknown
 * sort field answers 400 and an over-wide window answers 400 — so this exists
 * to avoid offering a control that would fail, never to decide what is allowed.
 *
 * Keep in step with `ReportSorts`, `ReportProperties` and `TrendGranularity`.
 */

/** `ReportProperties.maxPageSize` is 100; twenty is the platform's page. */
export const PAGE_SIZE = 20

/** `ReportProperties.defaultPeriodDays`, for labelling an unfiltered window. */
export const DEFAULT_PERIOD_DAYS = 30

/** `ReportProperties.maxPeriodDays`. A wider window is refused, not truncated. */
export const MAX_PERIOD_DAYS = 366

interface SortOption {
  value: string
  label: string
}

/** `ReportSorts.PROJECTS`, with the module's own default first. */
export const PROJECT_SORTS: readonly SortOption[] = [
  { value: 'progress,desc', label: 'Furthest along' },
  { value: 'progress,asc', label: 'Least progress' },
  { value: 'name,asc', label: 'Name A to Z' },
  { value: 'key,asc', label: 'Key' },
  { value: 'status,asc', label: 'Status' },
  { value: 'updatedAt,desc', label: 'Recently updated' },
]

export const DEFAULT_PROJECT_SORT = 'progress,desc'

/** `ReportSorts.OVERDUE`. Longest overdue first is what the module returns. */
export const OVERDUE_SORTS: readonly SortOption[] = [
  { value: 'dueDate,asc', label: 'Longest overdue' },
  { value: 'dueDate,desc', label: 'Most recently due' },
  { value: 'priority,desc', label: 'Highest priority' },
  { value: 'title,asc', label: 'Title A to Z' },
  { value: 'projectId,asc', label: 'Grouped by project' },
]

export const DEFAULT_OVERDUE_SORT = 'dueDate,asc'

/**
 * `ReportSorts.WORKLOAD`.
 *
 * Sorted in the reports module rather than in SQL, because every column but the
 * name is an aggregate. That changes nothing for a caller, but it is why the
 * allowlist is short.
 */
export const WORKLOAD_SORTS: readonly SortOption[] = [
  { value: 'open,desc', label: 'Busiest first' },
  { value: 'overdue,desc', label: 'Most overdue' },
  { value: 'completedInPeriod,desc', label: 'Most completed' },
  { value: 'fullName,asc', label: 'Name A to Z' },
]

export const DEFAULT_WORKLOAD_SORT = 'open,desc'

/** The three `TrendGranularity` accepts. Absent means the backend chooses. */
export const GRANULARITIES: readonly { value: Granularity; label: string }[] = [
  { value: 'DAY', label: 'Daily' },
  { value: 'WEEK', label: 'Weekly' },
  { value: 'MONTH', label: 'Monthly' },
]

export function isGranularity(value: string): value is Granularity {
  return GRANULARITIES.some((option) => option.value === value)
}

/**
 * The project lifecycle, for the one filter that needs it.
 *
 * The project report filters by status but returns no breakdown, so the values
 * have to be named here before they can be offered. Task statuses and
 * priorities arrive from the backend already labelled and are never hard-coded.
 *
 * Keep in step with `ProjectStatus`.
 */
export const PROJECT_STATUSES: readonly { value: string; label: string }[] = [
  { value: 'PLANNING', label: 'Planning' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ON_HOLD', label: 'On hold' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export function isProjectStatus(value: string): boolean {
  return PROJECT_STATUSES.some((status) => status.value === value)
}
