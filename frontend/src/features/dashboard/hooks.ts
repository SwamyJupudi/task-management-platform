import { useQuery, type UseQueryResult } from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { usePermissions } from '@/hooks/use-permissions'
import { queryKeys } from '@/lib/query-client'

import * as dashboardApi from './api'
import type { EmployeeDashboard, ProjectStatusCount, WorkspaceDashboard } from './types'

/**
 * The dashboard's server state, all of it through TanStack Query.
 *
 * Every key carries the workspace id. Two workspaces are two different answers
 * to the same question, and a key that left the workspace out would serve one
 * workspace's figures under the other's name for as long as the entry stayed in
 * the cache.
 *
 * `enabled` repeats the permission the endpoint is gated on, so the interface
 * does not make a request it already knows will be refused. That saves a
 * pointless 403 in the console; it secures nothing, and the backend checks
 * again regardless.
 *
 * Dashboards are a snapshot of a moving system, so they go stale quickly: a
 * minute, and a refetch when the window is focused again. Anything longer and a
 * screen left open over lunch quietly reports the morning.
 */

const STALE_MS = 60_000

/** The three codes `requireWorkspaceDashboard` insists on, all of them. */
export const WORKSPACE_DASHBOARD_PERMISSIONS = [
  'project:read_any',
  'member:read',
  'team:read',
] as const

/** The pair `requireProjectReadAccess` insists on for the project report. */
const PROJECT_REPORT_PERMISSIONS = ['project:read', 'task:read'] as const

/** True when the current user may open the workspace-wide dashboard. */
export function useCanSeeWorkspaceDashboard(): boolean {
  const { hasAll } = usePermissions()
  return hasAll(WORKSPACE_DASHBOARD_PERMISSIONS)
}

/** True when the current user may open their own dashboard. */
export function useCanSeeMyDashboard(): boolean {
  const { has } = usePermissions()
  return has('task:read')
}

/** The caller's own work in the active workspace. */
export function useEmployeeDashboard(): UseQueryResult<EmployeeDashboard> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const permitted = useCanSeeMyDashboard()

  return useQuery({
    queryKey: [...queryKeys.dashboard, 'me', workspaceId],
    queryFn: () => dashboardApi.employeeDashboard(workspaceId as string),
    enabled: workspaceId !== null && permitted,
    staleTime: STALE_MS,
    refetchOnWindowFocus: true,
  })
}

/** The whole workspace, for somebody entitled to see the whole workspace. */
export function useWorkspaceDashboard(): UseQueryResult<WorkspaceDashboard> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const permitted = useCanSeeWorkspaceDashboard()

  return useQuery({
    queryKey: [...queryKeys.dashboard, 'workspace', workspaceId],
    queryFn: () => dashboardApi.workspaceDashboard(workspaceId as string),
    enabled: workspaceId !== null && permitted,
    staleTime: STALE_MS,
    refetchOnWindowFocus: true,
  })
}

/**
 * Projects by lifecycle state.
 *
 * A query of its own rather than part of the workspace dashboard, because it is
 * a different endpoint behind a different permission. Keeping it separate is
 * what lets the panel fail alone: a caller who holds the dashboard's three
 * codes but not `project:read` sees every other panel and an error in this one.
 */
export function useProjectStatusDistribution(): UseQueryResult<ProjectStatusCount[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { hasAll } = usePermissions()

  return useQuery({
    queryKey: [...queryKeys.dashboard, 'project-status', workspaceId],
    queryFn: () => dashboardApi.projectStatusCounts(workspaceId as string),
    enabled: workspaceId !== null && hasAll(PROJECT_REPORT_PERMISSIONS),
    staleTime: STALE_MS,
  })
}
