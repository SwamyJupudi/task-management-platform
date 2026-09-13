import { api, type Page } from '@/lib/api'

import type {
  CreateSubtaskInput,
  CreateTaskInput,
  Subtask,
  Task,
  TaskFilters,
  TaskPageRequest,
  UpdateSubtaskInput,
  UpdateTaskInput,
} from './types'

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

// --- subtasks ---------------------------------------------------------------

/**
 * The checklist under a task.
 *
 * No permission family of its own: a subtask is part of a task rather than a
 * thing to hold rights over separately, so editing the checklist needs
 * `task:update` and ticking an item off needs `task:change_status` — exactly
 * what the same two operations need on the task itself.
 *
 * The list is not paged. A checklist that needs a second page is not a
 * checklist, and the backend returns a plain array to say so.
 */

const subtasksBase = (workspaceId: string, taskId: string) =>
  `${base(workspaceId)}/tasks/${taskId}/subtasks`

/** `GET /tasks/{taskId}/subtasks`. In checklist order. */
export function listSubtasks(workspaceId: string, taskId: string): Promise<Subtask[]> {
  return api.get<Subtask[]>(subtasksBase(workspaceId, taskId))
}

/** `POST /tasks/{taskId}/subtasks`. Starts in TODO, at the end of the checklist. */
export function createSubtask(
  workspaceId: string,
  taskId: string,
  body: CreateSubtaskInput,
): Promise<Subtask> {
  return api.post<Subtask>(subtasksBase(workspaceId, taskId), body)
}

/** `PATCH /tasks/{taskId}/subtasks/{id}`. Omitted fields are left alone. */
export function updateSubtask(
  workspaceId: string,
  taskId: string,
  subtaskId: string,
  body: UpdateSubtaskInput,
): Promise<Subtask> {
  return api.patch<Subtask>(`${subtasksBase(workspaceId, taskId)}/${subtaskId}`, body)
}

/**
 * `POST /tasks/{taskId}/subtasks/{id}/status`.
 *
 * The same state machine as a task, and moving to the status it already holds
 * is a conflict rather than a no-op — so the caller must have something to
 * change before asking.
 */
export function changeSubtaskStatus(
  workspaceId: string,
  taskId: string,
  subtaskId: string,
  status: string,
): Promise<Subtask> {
  return api.post<Subtask>(`${subtasksBase(workspaceId, taskId)}/${subtaskId}/status`, { status })
}

/** `DELETE /tasks/{taskId}/subtasks/{id}`. Soft delete; it stops counting. */
export function deleteSubtask(
  workspaceId: string,
  taskId: string,
  subtaskId: string,
): Promise<void> {
  return api.delete<void>(`${subtasksBase(workspaceId, taskId)}/${subtaskId}`)
}

// --- dependencies -----------------------------------------------------------

/**
 * Recording that one task waits on another in the same project.
 *
 * Also no permission family of its own: reading them needs `task:read` and
 * changing them `task:update`, through the same guard as everything else about
 * the task.
 *
 * There is no read here on purpose. `GET /tasks/{id}/dependencies` exists, but
 * both directions already arrive on the task itself, and that copy carries the
 * full `PLAT-12` key while the dedicated endpoint returns the number alone.
 * Reading the task and invalidating it after a write shows more, not less.
 */

/**
 * `POST /tasks/{taskId}/dependencies`.
 *
 * Refused for the task itself, a duplicate, a task in another project, and
 * anything that would close a cycle. Each refusal arrives as a sentence, which
 * is what the interface shows rather than trying to pre-empt the graph.
 */
export function addDependency(
  workspaceId: string,
  taskId: string,
  dependsOnTaskId: string,
): Promise<unknown> {
  return api.post<unknown>(`${base(workspaceId)}/tasks/${taskId}/dependencies`, {
    dependsOnTaskId,
  })
}

/**
 * `DELETE /tasks/{taskId}/dependencies/{dependsOnTaskId}`.
 *
 * The edge is owned by the waiting task, so removing "B is blocking A" means
 * calling this on A. The panel does that when the row being removed is on the
 * blocking side.
 */
export function removeDependency(
  workspaceId: string,
  taskId: string,
  dependsOnTaskId: string,
): Promise<void> {
  return api.delete<void>(`${base(workspaceId)}/tasks/${taskId}/dependencies/${dependsOnTaskId}`)
}
