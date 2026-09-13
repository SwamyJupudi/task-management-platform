import { api, type Page } from '@/lib/api'

import type {
  CreateProjectInput,
  Project,
  ProjectFilters,
  ProjectMember,
  ProjectPageRequest,
  UpdateProjectInput,
} from './types'

/**
 * Every project endpoint, in one place.
 *
 * Thin on purpose: each function is a path, a body and a return type. The
 * transport, the error envelope and the bearer header belong to the shared
 * client, and what to do with a result belongs to the hooks, so these stay
 * usable outside React.
 *
 * Every path is workspace-scoped, matching the backend, which takes the
 * workspace from the path and never from a header. A listing returns what the
 * caller may see rather than everything in the workspace: that narrowing is the
 * guard's decision and the query's job, never a filter applied here.
 */

const base = (workspaceId: string) => `/workspaces/${workspaceId}/projects`

/** `GET /projects`. Filtered, sorted and paged by the server. */
export function listProjects(
  workspaceId: string,
  filters: ProjectFilters,
  page: ProjectPageRequest,
): Promise<Page<Project>> {
  return api.get<Page<Project>>(base(workspaceId), {
    // Null and undefined entries are dropped by the client, so an unset filter
    // simply does not appear in the query string.
    params: {
      status: filters.status,
      priority: filters.priority,
      teamId: filters.teamId,
      ownerUserId: filters.ownerUserId,
      q: filters.q,
      label: filters.label,
      page: page.page,
      size: page.size,
      sort: page.sort,
    },
  })
}

/** `GET /projects/{id}`. Answers 404 for a project the caller may not see. */
export function getProject(workspaceId: string, projectId: string): Promise<Project> {
  return api.get<Project>(`${base(workspaceId)}/${projectId}`)
}

/** `POST /projects`. Always starts in PLANNING; a named owner joins it. */
export function createProject(workspaceId: string, body: CreateProjectInput): Promise<Project> {
  return api.post<Project>(base(workspaceId), body)
}

/** `PATCH /projects/{id}`. Omitted fields are left alone; labels replace the set. */
export function updateProject(
  workspaceId: string,
  projectId: string,
  body: UpdateProjectInput,
): Promise<Project> {
  return api.patch<Project>(`${base(workspaceId)}/${projectId}`, body)
}

/**
 * `POST /projects/{id}/status`.
 *
 * Its own endpoint rather than a field on the edit, because the move is checked
 * against the state machine and a rejected one answers 409 rather than a
 * validation error.
 */
export function changeProjectStatus(
  workspaceId: string,
  projectId: string,
  status: string,
): Promise<Project> {
  return api.post<Project>(`${base(workspaceId)}/${projectId}/status`, { status })
}

/** `DELETE /projects/{id}`. Soft delete; the key and name become free again. */
export function deleteProject(workspaceId: string, projectId: string): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/${projectId}`)
}

/** `GET /projects/{id}/members`. */
export function listProjectMembers(
  workspaceId: string,
  projectId: string,
  page = 0,
  size = 50,
): Promise<Page<ProjectMember>> {
  return api.get<Page<ProjectMember>>(`${base(workspaceId)}/${projectId}/members`, {
    params: { page, size },
  })
}

/** `POST /projects/{id}/members`. The person must already be in the workspace. */
export function addProjectMember(
  workspaceId: string,
  projectId: string,
  userId: string,
): Promise<ProjectMember> {
  return api.post<ProjectMember>(`${base(workspaceId)}/${projectId}/members`, { userId })
}

/** `DELETE /projects/{id}/members/{userId}`. Refused if they own the project. */
export function removeProjectMember(
  workspaceId: string,
  projectId: string,
  userId: string,
): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/${projectId}/members/${userId}`)
}

/** `PUT /projects/{id}/owner`. Adds them to the project if they are not on it. */
export function assignProjectOwner(
  workspaceId: string,
  projectId: string,
  userId: string,
): Promise<Project> {
  return api.put<Project>(`${base(workspaceId)}/${projectId}/owner`, { userId })
}

/** `DELETE /projects/{id}/owner`. They stay a member of the project. */
export function clearProjectOwner(workspaceId: string, projectId: string): Promise<Project> {
  return api.delete<Project>(`${base(workspaceId)}/${projectId}/owner`)
}
