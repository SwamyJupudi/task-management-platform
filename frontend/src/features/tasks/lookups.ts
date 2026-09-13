import { api, type Page } from '@/lib/api'

import type { ProjectMemberOption, ProjectOption } from './types'

/**
 * The two read-only lookups the task screens need but do not own.
 *
 * A task belongs to a project and is assigned to somebody on that project, so
 * the filters and the pickers have to be able to list both. Neither belongs to
 * this feature, and the projects feature keeps its own equivalents private, so
 * these are here rather than reaching across a feature boundary for them — the
 * same arrangement `features/projects/lookups.ts` makes for teams and the
 * workspace roster, and the same file to delete when shared lookups earn a
 * home of their own.
 *
 * Both are gated on `project:read`, which reading tasks already implies in
 * practice but does not require. A caller without it loses the pickers and
 * keeps the task list.
 */

/** `GET /workspaces/{id}/projects`. The project filter and the create dialog. */
export async function listProjectOptions(workspaceId: string): Promise<ProjectOption[]> {
  const page = await api.get<Page<ProjectOption>>(`/workspaces/${workspaceId}/projects`, {
    params: { page: 0, size: 100, sort: 'name,asc' },
  })
  return page.content
}

/**
 * `GET /workspaces/{id}/projects/{projectId}/members`.
 *
 * The assignee picker lists these rather than the workspace roster, because
 * the backend refuses an assignee who is not on the task's project. Offering
 * the whole workspace would mean most choices failing.
 */
export async function listProjectMemberOptions(
  workspaceId: string,
  projectId: string,
): Promise<ProjectMemberOption[]> {
  const page = await api.get<Page<ProjectMemberOption>>(
    `/workspaces/${workspaceId}/projects/${projectId}/members`,
    { params: { page: 0, size: 100 } },
  )
  return page.content
}

/**
 * One project, for the permission check on a task's detail screen.
 *
 * `TaskAccessGuard` lets the project's owner change any task in it, and the
 * task response does not carry the owner, so the detail screen fetches the
 * project to answer that half of the rule. One request, and the screen shows
 * the project's name beside the task anyway.
 */
export interface ProjectOwnership {
  id: string
  ownerUserId: string | null
  teamId: string | null
}

export function getProjectOwnership(
  workspaceId: string,
  projectId: string,
): Promise<ProjectOwnership> {
  return api.get<ProjectOwnership>(`/workspaces/${workspaceId}/projects/${projectId}`)
}
