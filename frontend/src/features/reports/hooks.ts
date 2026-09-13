import { useQuery, type UseQueryResult } from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { usePermissions } from '@/hooks/use-permissions'
import { type Page } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'

import * as reportsApi from './api'
import { PAGE_SIZE } from './constants'
import * as lookups from './lookups'
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
 * The reports feature's server state, all of it through TanStack Query.
 *
 * Every key carries the workspace id and the filters the answer was computed
 * for. Two workspaces are two different answers to the same question, and so
 * are two windows of the same workspace; a key that left either out would serve
 * one report under another's name for as long as it stayed in the cache.
 *
 * `enabled` repeats the permission the endpoint is gated on, so the interface
 * does not issue a request it already knows will be refused. That saves a
 * pointless 403; it secures nothing, and the backend checks again regardless.
 *
 * A report is a snapshot of a moving system, so it goes stale in a minute. It
 * does not refetch when the window is focused again, unlike the dashboard: a
 * report is read rather than watched, and figures that move while somebody is
 * comparing two rows are worse than figures a minute old.
 */

const STALE_MS = 60_000

function reportsRoot(workspaceId: string) {
  return [...queryKeys.reports, workspaceId] as const
}

// --- permissions ------------------------------------------------------------

/**
 * What the current user may read here, in the backend's own codes.
 *
 * `ReportAccessGuard` adds no permission of its own: a report is computed over
 * the caller's project read scope, and `project:read_any` already widens that
 * to the whole workspace. So there is no `report:read` to check, and every
 * screen here is gated on the grant for the records it aggregates.
 *
 * The distinction that matters on these screens is between a refusal and an
 * empty answer. Somebody who holds `task:read` but reaches no project passes
 * every gate and is answered zeros and empty pages rather than 403, so an empty
 * report must read as "nothing here yet" and never as "you are not allowed".
 */
export interface ReportPermissions {
  /** Every report listing, and both task breakdowns. */
  canReadReports: boolean
  /** The project report insists on both of its codes, not either. */
  canReadProjectReport: boolean
  /** The three codes the workspace dashboard, where team performance lives, insists on. */
  canReadTeamPerformance: boolean
  /** Enough to fill the team filter. */
  canReadTeams: boolean
  /** Enough to fill the project filter. */
  canReadProjects: boolean
  /** Enough to fill the person filter. */
  canReadMembers: boolean
}

/** The pair `requireProjectReadAccess` insists on. */
const PROJECT_REPORT_CODES = ['project:read', 'task:read'] as const

/** The three `requireWorkspaceDashboard` insists on. */
const TEAM_PERFORMANCE_CODES = ['project:read_any', 'member:read', 'team:read'] as const

export function useReportPermissions(): ReportPermissions {
  const { has, hasAll } = usePermissions()

  return {
    canReadReports: has('task:read'),
    canReadProjectReport: hasAll(PROJECT_REPORT_CODES),
    canReadTeamPerformance: hasAll(TEAM_PERFORMANCE_CODES),
    canReadTeams: has('team:read'),
    canReadProjects: has('project:read'),
    canReadMembers: has('member:read'),
  }
}

// --- reports ----------------------------------------------------------------

/** Projects with their progress and their task counts, paged. */
export function useProjectReport(
  filters: ProjectReportFilters,
  page: number,
  sort: string,
): UseQueryResult<Page<ProjectProgress>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadProjectReport } = useReportPermissions()

  return useQuery({
    queryKey: [...reportsRoot(workspaceId ?? 'none'), 'projects', filters, page, sort],
    queryFn: () => reportsApi.projectReport(workspaceId as string, filters, page, PAGE_SIZE, sort),
    enabled: workspaceId !== null && canReadProjectReport,
    staleTime: STALE_MS,
    // Keeps the table on screen while the next page or a changed filter loads,
    // rather than collapsing to a skeleton and back.
    placeholderData: (previous) => previous,
  })
}

/** Tasks by status and by priority, over one scope and one optional window. */
export function useTaskDistribution(filters: TaskReportFilters): UseQueryResult<Distribution> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadReports } = useReportPermissions()

  return useQuery({
    queryKey: [...reportsRoot(workspaceId ?? 'none'), 'distribution', filters],
    queryFn: () => reportsApi.taskDistribution(workspaceId as string, filters),
    enabled: workspaceId !== null && canReadReports,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/** What arrived and what was finished, bucket by bucket. */
export function useTrendReport(
  filters: TaskReportFilters,
  granularity: Granularity | undefined,
): UseQueryResult<TrendPoint[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadReports } = useReportPermissions()

  return useQuery({
    queryKey: [...reportsRoot(workspaceId ?? 'none'), 'trends', filters, granularity ?? 'auto'],
    queryFn: () => reportsApi.trendReport(workspaceId as string, filters, granularity),
    enabled: workspaceId !== null && canReadReports,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/** Every task past its date and not finished, paged. */
export function useOverdueReport(
  filters: OverdueReportFilters,
  page: number,
  sort: string,
): UseQueryResult<Page<OverdueTask>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadReports } = useReportPermissions()

  return useQuery({
    queryKey: [...reportsRoot(workspaceId ?? 'none'), 'overdue', filters, page, sort],
    queryFn: () => reportsApi.overdueReport(workspaceId as string, filters, page, PAGE_SIZE, sort),
    enabled: workspaceId !== null && canReadReports,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/** One row per person: what they are carrying, and what they finished. */
export function useWorkloadReport(
  filters: WorkloadReportFilters,
  page: number,
  sort: string,
): UseQueryResult<Page<Workload>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadReports } = useReportPermissions()

  return useQuery({
    queryKey: [...reportsRoot(workspaceId ?? 'none'), 'workload', filters, page, sort],
    queryFn: () => reportsApi.workloadReport(workspaceId as string, filters, page, PAGE_SIZE, sort),
    enabled: workspaceId !== null && canReadReports,
    staleTime: STALE_MS,
    placeholderData: (previous) => previous,
  })
}

/**
 * One team's own figures, and its members' workload.
 *
 * Gated on `team:read` here, which is only half the backend's rule: it also
 * asks that the caller leads the team or holds `project:read_any`, and answers
 * 404 rather than 403 when they do not. That half is deliberately not repeated,
 * because answering it on this side would mean fetching the team to learn who
 * leads it. The screen renders the not-found state instead, which is exactly
 * what the backend is saying.
 */
export function useTeamDashboard(teamId: string | undefined): UseQueryResult<TeamDashboard> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadTeams } = useReportPermissions()

  return useQuery({
    queryKey: [...queryKeys.teams, workspaceId, 'dashboard', teamId],
    queryFn: () => reportsApi.teamDashboard(workspaceId as string, teamId as string),
    enabled: workspaceId !== null && teamId !== undefined && teamId !== '' && canReadTeams,
    staleTime: STALE_MS,
  })
}

// --- lookups ----------------------------------------------------------------

/**
 * The projects and the teams a filter can name.
 *
 * Cached for five minutes rather than one. A dropdown's contents change when
 * somebody creates a project, which is far rarer than the figures moving, and
 * refetching a hundred names beside every report would be waste.
 */
export function useProjectOptions(): UseQueryResult<lookups.ProjectOption[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadProjects } = useReportPermissions()

  return useQuery({
    queryKey: [...queryKeys.projects, workspaceId, 'options'],
    queryFn: () => lookups.listProjectOptions(workspaceId as string),
    enabled: workspaceId !== null && canReadProjects,
    staleTime: 5 * 60_000,
  })
}

export function useTeamOptions(): UseQueryResult<lookups.TeamOption[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadTeams } = useReportPermissions()

  return useQuery({
    queryKey: [...queryKeys.teams, workspaceId, 'options'],
    queryFn: () => lookups.listTeamOptions(workspaceId as string),
    enabled: workspaceId !== null && canReadTeams,
    staleTime: 5 * 60_000,
  })
}
