import { api, type Page } from '@/lib/api'

import type { CreateTaskInput, Task, TaskFilters, TaskPageRequest, UpdateTaskInput } from './types'

/**
 * Every task endpoint this phase uses, in one place.
 *
 * Thin on purpose: each function is a path, a body and a return type. The
 * transport, the error envelope and the bearer header belong to the shared
 * client, and what to do with a result belongs to the hooks.
 *
 * Listing goes through the workspace-wide endpoint even when a project is
 * chosen, because that endpoint takes `projectId` as a filter and returns the
 * same shape. The project-scoped listing exists too and is what a board inside
 * one project would use; one path here means one set of filters, one sort and
 * one page envelope to reason about.
 *
 * A listing returns what the caller may see rather than everything in the
 * workspace. That narrowing is the guard's decision and the query's job, never
 * a filter applied here.
 */

const base = (workspaceId: string) => `/workspaces/${workspaceId}`

/** `GET /tasks`. Filtered, sorted and paged by the server. */
export function listTasks(
  workspaceId: string,
  filters: TaskFilters,
  page: TaskPageRequest,
): Promise<Page<Task>> {
  return api.get<Page<Task>>(`${base(workspaceId)}/tasks`, {
    // Null and undefined entries are dropped by the client, so an unset filter
    // simply does not appear in the query string. The booleans are only sent
    // when true, since the server already defaults them to false.
    params: {
      projectId: filters.projectId,
      teamId: filters.teamId,
      status: filters.status,
      priority: filters.priority,
      assigneeUserId: filters.assigneeUserId,
      unassigned: filters.unassigned === true ? true : undefined,
      reporterUserId: filters.reporterUserId,
      dueBefore: filters.dueBefore,
      dueAfter: filters.dueAfter,
      overdue: filters.overdue === true ? true : undefined,
      label: filters.label,
      blocked: filters.blocked === true ? true : undefined,
      q: filters.q,
      page: page.page,
      size: page.size,
      sort: page.sort,
    },
  })
}

/** `GET /tasks/{id}`. Answers 404 for a task the caller may not see. */
export function getTask(workspaceId: string, taskId: string): Promise<Task> {
  return api.get<Task>(`${base(workspaceId)}/tasks/${taskId}`)
}

/**
 * `POST /projects/{projectId}/tasks`.
 *
 * The project comes from the path rather than the body, because it is what the
 * task is numbered against. A new task always starts in TODO: letting the
 * caller choose would make the transition rules optional.
 */
export function createTask(
  workspaceId: string,
  projectId: string,
  body: CreateTaskInput,
): Promise<Task> {
  return api.post<Task>(`${base(workspaceId)}/projects/${projectId}/tasks`, body)
}

/** `PATCH /tasks/{id}`. Omitted fields are left alone; labels replace the set. */
export function updateTask(
  workspaceId: string,
  taskId: string,
  body: UpdateTaskInput,
): Promise<Task> {
  return api.patch<Task>(`${base(workspaceId)}/tasks/${taskId}`, body)
}

/**
 * `POST /tasks/{id}/status`.
 *
 * Its own endpoint, and its own permission: changing status needs
 * `task:change_status` rather than `task:update`, so somebody may move work
 * along without being able to rewrite it.
 */
export function changeTaskStatus(
  workspaceId: string,
  taskId: string,
  status: string,
): Promise<Task> {
  return api.post<Task>(`${base(workspaceId)}/tasks/${taskId}/status`, { status })
}

/** `PUT /tasks/{id}/assignee`. They must already be on the task's project. */
export function assignTask(
  workspaceId: string,
  taskId: string,
  assigneeUserId: string,
): Promise<Task> {
  return api.put<Task>(`${base(workspaceId)}/tasks/${taskId}/assignee`, { assigneeUserId })
}

/** `DELETE /tasks/{id}/assignee`. Clearing is a delete, not a null assign. */
export function unassignTask(workspaceId: string, taskId: string): Promise<Task> {
  return api.delete<Task>(`${base(workspaceId)}/tasks/${taskId}/assignee`)
}
