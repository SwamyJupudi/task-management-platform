import { api, type Page } from '@/lib/api'

import type { TeamOption, WorkspaceMemberOption } from './types'

/**
 * The two read-only lookups the project screens need but do not own.
 *
 * A project names a team and an owner, so the pickers and the filters have to
 * be able to list both. Neither belongs to this feature: when the teams and
 * people features are built they will own these endpoints, and this file is
 * what gets deleted rather than a set of calls scattered through the project
 * components.
 *
 * Both are gated on their own permission — `team:read` and `member:read` — and
 * neither is required to work with projects. A caller without them keeps every
 * project control and loses only the pickers, which is why the hooks that wrap
 * these fail quietly rather than failing the screen.
 */

/** `GET /workspaces/{id}/teams`. Needs `team:read`. */
export async function listTeams(workspaceId: string): Promise<TeamOption[]> {
  const page = await api.get<Page<TeamOption>>(`/workspaces/${workspaceId}/teams`, {
    params: { page: 0, size: 100, sort: 'name,asc' },
  })
  return page.content
}

/** `GET /workspaces/{id}/members`. Needs `member:read`. */
export async function listWorkspaceMembers(workspaceId: string): Promise<WorkspaceMemberOption[]> {
  const page = await api.get<Page<WorkspaceMemberOption>>(`/workspaces/${workspaceId}/members`, {
    params: { page: 0, size: 100 },
  })
  return page.content
}
