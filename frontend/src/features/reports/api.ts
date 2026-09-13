import { api, type Page } from '@/lib/api'

import type {
  Distribution,
  Granularity,
  OverdueReportFilters,
  OverdueTask,
  ProjectProgress,
  ProjectReportFilters,
  TaskReportFilters,
  TeamDashboard,
  TrendPoint,
  Workload,
  WorkloadReportFilters,
} from './types'

/**
 * Every report endpoint, in one place.
 *
 * Thin on purpose: each function is a path, its parameters and a return type.
 * The transport, the error envelope and the bearer header are the shared
 * client's business, and nothing here decides what to do with a result, so
 * these stay usable outside React.
 *
 * The workspace comes from the caller rather than from ambient state, matching
 * the backend, which takes it from the path and never from a header.
 *
 * An undefined parameter is dropped by the client rather than sent empty, which
 * is what lets a screen pass its whole filter object and have "no filter" mean
 * the same thing as not asking.
 */

const base = (workspaceId: string) => `/workspaces/${workspaceId}/reports`

/**
 * `GET /reports/projects`.
 *
 * Needs `project:read` and `task:read`, both: the report shows a project's
 * progress beside counts derived from its tasks, so a caller who could obtain
 * neither half from its own listing should not obtain the pair here.
 *
 * Sortable by `name`, `key`, `status`, `progress` and `updatedAt`; anything
 * else is refused with 400 rather than ignored.
 */
export function projectReport(
  workspaceId: string,
  filters: ProjectReportFilters,
  page: number,
  size: number,
  sort: string,
): Promise<Page<ProjectProgress>> {
  return api.get<Page<ProjectProgress>>(`${base(workspaceId)}/projects`, {
    params: {
      status: filters.status,
      teamId: filters.teamId,
      ownerUserId: filters.ownerUserId,
      page,
      size,
      sort,
    },
  })
}

/**
 * `GET /reports/tasks/distribution`.
 *
 * Both breakdowns in one body because they are two views of one question: a
 * task finished between two requests would be counted in one chart and not the
 * other, and the pair would not add up.
 */
export function taskDistribution(
  workspaceId: string,
  filters: TaskReportFilters,
): Promise<Distribution> {
  return api.get<Distribution>(`${base(workspaceId)}/tasks/distribution`, {
    params: {
      projectId: filters.projectId,
      teamId: filters.teamId,
      assigneeUserId: filters.assigneeUserId,
      from: filters.from,
      to: filters.to,
    },
  })
}

/**
 * `GET /reports/tasks/overdue`.
 *
 * Sortable by `dueDate`, `priority`, `projectId` and `title`, longest overdue
 * first by default.
 */
export function overdueReport(
  workspaceId: string,
  filters: OverdueReportFilters,
  page: number,
  size: number,
  sort: string,
): Promise<Page<OverdueTask>> {
  return api.get<Page<OverdueTask>>(`${base(workspaceId)}/tasks/overdue`, {
    params: {
      projectId: filters.projectId,
      teamId: filters.teamId,
      assigneeUserId: filters.assigneeUserId,
      page,
      size,
      sort,
    },
  })
}

/**
 * `GET /reports/workload`.
 *
 * Sortable by `open`, `overdue`, `completedInPeriod` and `fullName`, busiest
 * first by default.
 */
export function workloadReport(
  workspaceId: string,
  filters: WorkloadReportFilters,
  page: number,
  size: number,
  sort: string,
): Promise<Page<Workload>> {
  return api.get<Page<Workload>>(`${base(workspaceId)}/workload`, {
    params: {
      projectId: filters.projectId,
      teamId: filters.teamId,
      from: filters.from,
      to: filters.to,
      page,
      size,
      sort,
    },
  })
}

/**
 * `GET /reports/trends`. A list rather than a page: a window is already bounded.
 *
 * Granularity is optional, and leaving it out is usually right — the backend
 * chooses days for a short window and weeks for a long one, which is a decision
 * it can make because it has already resolved the window.
 */
export function trendReport(
  workspaceId: string,
  filters: TaskReportFilters,
  granularity: Granularity | undefined,
): Promise<TrendPoint[]> {
  return api.get<TrendPoint[]>(`${base(workspaceId)}/trends`, {
    params: {
      projectId: filters.projectId,
      teamId: filters.teamId,
      assigneeUserId: filters.assigneeUserId,
      from: filters.from,
      to: filters.to,
      granularity,
    },
  })
}

/**
 * `GET /workspaces/{id}/teams/{teamId}/dashboard`.
 *
 * Not a report path, and not narrowed per viewer either: the figures are over
 * the team's projects whoever is asking. The gate is narrower instead —
 * `team:read` plus either leading the team or holding `project:read_any` — and
 * anybody else is answered 404 rather than 403, matching the platform rule that
 * an unreachable record is reported as missing.
 */
export function teamDashboard(workspaceId: string, teamId: string): Promise<TeamDashboard> {
  return api.get<TeamDashboard>(`/workspaces/${workspaceId}/teams/${teamId}/dashboard`)
}
