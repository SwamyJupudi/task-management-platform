import { api, type Page } from '@/lib/api'

/**
 * The two lists the report filters need but this feature does not own.
 *
 * A report can be narrowed to a project or to a team, so the filter bar has to
 * be able to name both. The third filter — a person — comes from the people
 * feature, which exports the workspace roster precisely so nothing grows a
 * fourth copy of that call.
 *
 * Both are gated on their own permission, `project:read` and `team:read`, and
 * neither is required to read a report. A caller without one loses that
 * dropdown and keeps the report, which is why the hooks around these fail
 * quietly rather than failing the screen.
 */

/** Only what a dropdown needs. The full records belong to their own features. */
export interface ProjectOption {
  id: string
  key: string
  name: string
}

export interface TeamOption {
  id: string
  name: string
}

/**
 * `GET /workspaces/{id}/projects`.
 *
 * One page of a hundred rather than every page. A workspace with more projects
 * than that has a filter this dropdown cannot serve well, and paging a
 * dropdown is a worse answer than the project report's own listing, which is
 * where somebody with that many projects is already going.
 */
export async function listProjectOptions(workspaceId: string): Promise<ProjectOption[]> {
  const page = await api.get<Page<ProjectOption>>(`/workspaces/${workspaceId}/projects`, {
    params: { page: 0, size: 100, sort: 'name,asc' },
  })
  return page.content
}

/** `GET /workspaces/{id}/teams`. Live teams first, by name. */
export async function listTeamOptions(workspaceId: string): Promise<TeamOption[]> {
  const page = await api.get<Page<TeamOption>>(`/workspaces/${workspaceId}/teams`, {
    params: { page: 0, size: 100, sort: 'name,asc' },
  })
  return page.content
}
