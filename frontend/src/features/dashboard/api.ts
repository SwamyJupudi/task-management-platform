import { api, type Page } from '@/lib/api'

import type {
  EmployeeDashboard,
  ProjectProgress,
  ProjectStatusCount,
  WorkspaceDashboard,
} from './types'

/**
 * The dashboard endpoints, in one place.
 *
 * Thin on purpose: each function is a path and a return type, and the
 * transport, the error envelope and the bearer header are the shared client's
 * business. Nothing here decides what to do with a result — that is the hooks'
 * job, so these stay usable outside React.
 *
 * Every path is workspace-scoped. The workspace comes from the caller rather
 * than from ambient state, matching the backend, which takes it from the path
 * and never from a header.
 */

/**
 * `GET /workspaces/{workspaceId}/dashboard/me`.
 *
 * Takes no user identifier, deliberately: there is no path, parameter or role
 * that renders one person's dashboard to another. Needs `task:read`.
 */
export function employeeDashboard(workspaceId: string): Promise<EmployeeDashboard> {
  return api.get<EmployeeDashboard>(`/workspaces/${workspaceId}/dashboard/me`)
}

/**
 * `GET /workspaces/{workspaceId}/dashboard/workspace`.
 *
 * Needs all three of `project:read_any`, `member:read` and `team:read`.
 * Somebody without them is refused rather than shown a narrowed version: a
 * workspace-wide figure computed over one person's projects would be a wrong
 * number rather than a discreet one.
 */
export function workspaceDashboard(workspaceId: string): Promise<WorkspaceDashboard> {
  return api.get<WorkspaceDashboard>(`/workspaces/${workspaceId}/dashboard/workspace`)
}

/**
 * The project lifecycle states, in the order a project moves through them.
 *
 * The one enum this feature has to know, because it is the one the backend
 * cannot label for it: the project report filters by status but returns no
 * breakdown, so the columns have to be named before they can be counted. Task
 * statuses and priorities arrive already labelled and are never hard-coded.
 */
const PROJECT_STATUSES: readonly ProjectStatusCount[] = [
  { status: 'PLANNING', label: 'Planning', count: 0 },
  { status: 'ACTIVE', label: 'Active', count: 0 },
  { status: 'ON_HOLD', label: 'On hold', count: 0 },
  { status: 'COMPLETED', label: 'Completed', count: 0 },
  { status: 'ARCHIVED', label: 'Archived', count: 0 },
]

/**
 * How many projects are in each state, and how many there are in total.
 *
 * Assembled from one count per status because no endpoint answers it. The
 * workspace dashboard reports only the active and completed figures, and its
 * `projectProgress` list is capped at the most recently touched, so counting
 * that list would produce a chart of a sample presented as a chart of the
 * whole — wrong, and wrong without saying so.
 *
 * Five requests instead, each asking for a single row and reading only
 * `totalElements`, which is a `COUNT` on the server rather than a page of
 * records. They run together, and the total is their sum: a project holds
 * exactly one status, and the enum is closed, so the parts add up to the whole
 * without a sixth request to check.
 *
 * Gated on `project:read` and `task:read` rather than on the dashboard's own
 * permissions, which is why the hook keeps this in a query of its own: a caller
 * who may see the workspace dashboard but not the project report loses this one
 * panel rather than the screen.
 */
export async function projectStatusCounts(workspaceId: string): Promise<ProjectStatusCount[]> {
  const counted = await Promise.all(
    PROJECT_STATUSES.map(async (column) => {
      const page = await api.get<Page<ProjectProgress>>(
        `/workspaces/${workspaceId}/reports/projects`,
        {
          params: { status: column.status, size: 1 },
        },
      )
      return { ...column, count: page.totalElements }
    }),
  )

  return counted
}
