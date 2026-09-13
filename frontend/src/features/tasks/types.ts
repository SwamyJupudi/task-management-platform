/**
 * The wire shapes the task endpoints exchange.
 *
 * Each mirrors a record in the Spring `tasks.dto` package. Nothing is reshaped
 * on the way in: `key` is composed server-side from the project key and the
 * task number, `blocked` is derived from the dependency graph, and both are
 * read rather than recomputed.
 */

/** The four board columns, as the backend's `TaskStatus` enum spells them. */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE'

/** The four levels, shared with projects. */
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

/**
 * One end of a dependency, as `TaskLinkResponse` carries it.
 *
 * The identifier is `id`, not `taskId` — this record names the task at the far
 * end of the edge rather than describing a relationship.
 *
 * `key` differs by where the link came from, which is worth knowing before
 * rendering it. On a task response it is the full `PLAT-12`, composed by the
 * mapper. On `GET /tasks/{id}/dependencies` it is the number alone, because
 * that endpoint answers about one task whose project the caller already knows.
 * Everything here reads the task response, so the full form is what is shown.
 */
export interface TaskLink {
  id: string
  taskNumber: number
  key: string
  title: string
  status: TaskStatus
}

/**
 * A task, with the people and the project already resolved.
 *
 * The assignee fields are null when nobody holds it, which is an ordinary state
 * rather than an error. So are the reporter fields, once the person who raised
 * it has left the workspace.
 *
 * Subtasks are deliberately not counted here — the backend says so explicitly,
 * and they are fetched from their own address when that feature exists.
 */
export interface Task {
  id: string
  workspaceId: string
  projectId: string
  projectKey: string
  projectName: string
  taskNumber: number
  /** The project key and the task number, e.g. `PLAT-12`. Composed, not stored. */
  key: string
  title: string
  description: string | null
  assigneeUserId: string | null
  assigneeEmail: string | null
  assigneeName: string | null
  reporterUserId: string | null
  reporterEmail: string | null
  reporterName: string | null
  status: TaskStatus
  priority: TaskPriority
  startDate: string | null
  dueDate: string | null
  estimatedMinutes: number | null
  actualMinutes: number | null
  boardPosition: number
  labels: string[]
  /** Tasks this one waits on. */
  blockedBy: TaskLink[]
  /** Tasks waiting on this one. */
  blocking: TaskLink[]
  /** True while an unfinished task blocks it. Surfaced, never enforced. */
  blocked: boolean
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

/** The body of `POST /projects/{projectId}/tasks`. */
export interface CreateTaskInput {
  title: string
  description?: string | undefined
  assigneeUserId?: string | undefined
  reporterUserId?: string | undefined
  priority?: string | undefined
  startDate?: string | undefined
  dueDate?: string | undefined
  estimatedMinutes?: number | undefined
  actualMinutes?: number | undefined
  labels?: string[] | undefined
}

/**
 * The body of `PATCH /tasks/{id}`. Every field optional; omitted is left alone.
 *
 * No `status` and no `assigneeUserId`: each has its own endpoint, because a
 * transition is checked against the state machine and assigning needs a
 * different permission from editing the rest.
 *
 * `clearDates` is the backend's way of removing both dates, since a null date
 * reads as "leave it alone". Unlike projects, tasks do have this flag.
 */
export interface UpdateTaskInput {
  title?: string | undefined
  description?: string | undefined
  priority?: string | undefined
  startDate?: string | undefined
  dueDate?: string | undefined
  /** Send true to clear both dates at once. */
  clearDates?: boolean | undefined
  estimatedMinutes?: number | undefined
  actualMinutes?: number | undefined
  boardPosition?: number | undefined
  labels?: string[] | undefined
}

/**
 * The filters the list endpoint accepts.
 *
 * `status` and `priority` are lists server-side and could each carry several
 * values; this phase sends at most one of each, which the client serialises as
 * a single repeated parameter either way.
 */
export interface TaskFilters {
  projectId?: string | undefined
  teamId?: string | undefined
  status?: TaskStatus | undefined
  priority?: TaskPriority | undefined
  assigneeUserId?: string | undefined
  unassigned?: boolean | undefined
  reporterUserId?: string | undefined
  dueBefore?: string | undefined
  dueAfter?: string | undefined
  overdue?: boolean | undefined
  label?: string | undefined
  blocked?: boolean | undefined
  /** Matches the title, or an exact `PROJ-12` key. */
  q?: string | undefined
}

/** A page request, in the shape the shared client sends it. */
export interface TaskPageRequest {
  page: number
  size: number
  /** `field,direction`, restricted to the backend's allowlist. */
  sort: string
}

/** A project, as the project filter and the create dialog need it. */
export interface ProjectOption {
  id: string
  key: string
  name: string
  status: string
}

/** Somebody on a project, as the assignee picker needs them. */
export interface ProjectMemberOption {
  userId: string
  email: string
  firstName: string
  lastName: string
  owner: boolean
}

/**
 * One checklist item under a task.
 *
 * `completed` is derived from the status by the backend rather than stored
 * beside it — they are one fact, and the response shape says so. The status
 * enum is the task's: the four board columns are the same four columns.
 */
export interface Subtask {
  id: string
  workspaceId: string
  projectId: string
  taskId: string
  title: string
  assigneeUserId: string | null
  assigneeEmail: string | null
  assigneeName: string | null
  status: TaskStatus
  /** True when the status is DONE. */
  completed: boolean
  dueDate: string | null
  /** Order in the checklist. */
  position: number
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

/** The body of `POST /tasks/{taskId}/subtasks`. */
export interface CreateSubtaskInput {
  title: string
  assigneeUserId?: string | undefined
  dueDate?: string | undefined
  position?: number | undefined
}

/**
 * The body of `PATCH /tasks/{taskId}/subtasks/{id}`.
 *
 * The assignee is a plain field here rather than an endpoint of its own, unlike
 * a task's, and clearing either the assignee or the due date is an explicit
 * flag because an omitted value means "leave it alone".
 */
export interface UpdateSubtaskInput {
  title?: string | undefined
  assigneeUserId?: string | undefined
  clearAssignee?: boolean | undefined
  dueDate?: string | undefined
  clearDueDate?: boolean | undefined
  position?: number | undefined
}
