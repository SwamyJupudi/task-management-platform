import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'

import { useActiveWorkspace } from '@/hooks/use-active-workspace'
import { usePermissions } from '@/hooks/use-permissions'
import { type Page } from '@/lib/api'
import { queryKeys } from '@/lib/query-client'
import { useSessionStore } from '@/stores/session-store'

import * as tasksApi from './api'
import * as lookupsApi from './lookups'
import type {
  CreateTaskInput,
  ProjectMemberOption,
  ProjectOption,
  Task,
  TaskFilters,
  TaskPageRequest,
  UpdateTaskInput,
} from './types'

/**
 * The task feature's server state, all of it through TanStack Query.
 *
 * Every key carries the workspace id. Two workspaces are two different answers
 * to the same question, and a key that left the workspace out would serve one
 * workspace's tasks under the other's name for as long as the entry stayed in
 * the cache.
 *
 * Mutations invalidate rather than patching the cache by hand. The backend
 * derives `key`, `blocked`, `completedAt` and `updatedAt`, and an optimistic
 * edit that guessed at those would show values the server never produced.
 */

const STALE_MS = 30_000

/** Everything under one workspace's tasks, for a broad invalidation. */
function tasksRoot(workspaceId: string) {
  return [...queryKeys.tasks, workspaceId] as const
}

// --- permissions ------------------------------------------------------------

/**
 * What the current user may do with tasks in the active workspace.
 *
 * Every answer comes from a permission code, never from a role name: the same
 * catalog gates the API, and a role is only one of the ways a code is held.
 *
 * Note that editing, moving and assigning are three different codes. Somebody
 * may be able to move work along without being able to rewrite it, which is a
 * distinction the interface has to keep rather than collapse into "can edit".
 */
export interface TaskPermissions {
  canRead: boolean
  canCreate: boolean
  canUpdate: boolean
  canChangeStatus: boolean
  canAssign: boolean
  /** True when the caller may act on every task, not only their own. */
  manageAny: boolean
  canReadProjects: boolean
}

export function useTaskPermissions(): TaskPermissions {
  const { has } = usePermissions()

  return {
    canRead: has('task:read'),
    canCreate: has('task:create'),
    canUpdate: has('task:update'),
    canChangeStatus: has('task:change_status'),
    canAssign: has('task:assign'),
    manageAny: has('task:manage_any'),
    canReadProjects: has('project:read'),
  }
}

/**
 * Whether the caller may change one particular task.
 *
 * Mirrors `TaskAccessGuard.requireChangeableTask`: holding the code is
 * necessary, and on top of it either `task:manage_any`, or being the assignee,
 * or the reporter, or the owner of the task's project or the lead of its team.
 *
 * The last of those needs the project, which the task response does not carry,
 * so `ownsOrLeadsProject` is passed in by whoever has already fetched it. The
 * detail screen does; the list does not, and therefore shows fewer controls
 * than a project owner may in fact use. Erring that way hides a control
 * somebody has rather than offering one they do not, and the backend remains
 * the only thing that decides.
 */
export function useTaskAbilities(task: Task | undefined, ownsOrLeadsProject = false) {
  const permissions = useTaskPermissions()
  const userId = useSessionStore((state) => state.user?.id ?? null)

  // Compared against a non-null user id, so an unassigned task and a signed-out
  // session cannot match each other through a pair of nulls.
  const isAssignee = userId !== null && task?.assigneeUserId === userId
  const isReporter = userId !== null && task?.reporterUserId === userId
  const reaches = permissions.manageAny || isAssignee || isReporter || ownsOrLeadsProject

  return {
    canEdit: permissions.canUpdate && reaches,
    canChangeStatus: permissions.canChangeStatus && reaches,
    canAssign: permissions.canAssign && reaches,
  }
}

// --- queries ----------------------------------------------------------------

export function useTasks(filters: TaskFilters, page: TaskPageRequest): UseQueryResult<Page<Task>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useTaskPermissions()

  return useQuery({
    queryKey: [...tasksRoot(workspaceId ?? 'none'), 'list', filters, page],
    queryFn: () => tasksApi.listTasks(workspaceId as string, filters, page),
    enabled: workspaceId !== null && canRead,
    staleTime: STALE_MS,
    // Keeps the previous page on screen while the next one loads, so paging and
    // typing in the search box do not blank the table on every keystroke.
    placeholderData: (previous) => previous,
  })
}

export function useTask(taskId: string | undefined): UseQueryResult<Task> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useTaskPermissions()

  return useQuery({
    queryKey: [...tasksRoot(workspaceId ?? 'none'), 'detail', taskId],
    queryFn: () => tasksApi.getTask(workspaceId as string, taskId as string),
    enabled: workspaceId !== null && taskId !== undefined && canRead,
    staleTime: STALE_MS,
  })
}

/** The workspace's projects, for the project filter and the create dialog. */
export function useProjectOptions(): UseQueryResult<ProjectOption[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadProjects } = useTaskPermissions()

  return useQuery({
    queryKey: [...queryKeys.projects, workspaceId, 'options'],
    queryFn: () => lookupsApi.listProjectOptions(workspaceId as string),
    enabled: workspaceId !== null && canReadProjects,
    staleTime: 5 * 60_000,
  })
}

/**
 * The roster of one project, for the assignee picker.
 *
 * Keyed by project, because the assignee must be on the task's project and two
 * projects have two different answers.
 */
export function useProjectMemberOptions(
  projectId: string | undefined,
): UseQueryResult<ProjectMemberOption[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadProjects } = useTaskPermissions()

  return useQuery({
    queryKey: [...queryKeys.projects, workspaceId, 'members', projectId],
    queryFn: () => lookupsApi.listProjectMemberOptions(workspaceId as string, projectId as string),
    enabled: workspaceId !== null && projectId !== undefined && projectId !== '' && canReadProjects,
    staleTime: 5 * 60_000,
  })
}

// --- mutations --------------------------------------------------------------

/**
 * Invalidates every task query in the workspace.
 *
 * Broad on purpose. A task's status, priority, assignee or dates decide which
 * filtered pages it belongs on, so a narrower invalidation would leave it
 * listed under a filter it no longer matches.
 */
function useInvalidateTasks() {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return () => {
    if (workspaceId === null) return
    void queryClient.invalidateQueries({ queryKey: tasksRoot(workspaceId) })
  }
}

export function useCreateTask(): UseMutationResult<
  Task,
  unknown,
  { projectId: string; body: CreateTaskInput }
> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateTasks()

  return useMutation({
    mutationFn: ({ projectId, body }) =>
      tasksApi.createTask(workspace?.workspaceId as string, projectId, body),
    onSuccess: invalidate,
  })
}

export function useUpdateTask(taskId: string): UseMutationResult<Task, unknown, UpdateTaskInput> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateTasks()

  return useMutation({
    mutationFn: (body: UpdateTaskInput) =>
      tasksApi.updateTask(workspace?.workspaceId as string, taskId, body),
    onSuccess: invalidate,
  })
}

export function useChangeTaskStatus(): UseMutationResult<
  Task,
  unknown,
  { taskId: string; status: string }
> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateTasks()

  return useMutation({
    mutationFn: ({ taskId, status }) =>
      tasksApi.changeTaskStatus(workspace?.workspaceId as string, taskId, status),
    onSuccess: invalidate,
  })
}

export function useAssignTask(taskId: string): UseMutationResult<Task, unknown, string | null> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateTasks()

  return useMutation({
    // Null clears it, which is a DELETE on the same address rather than a null
    // assignee in the body.
    mutationFn: (assigneeUserId: string | null) =>
      assigneeUserId === null
        ? tasksApi.unassignTask(workspace?.workspaceId as string, taskId)
        : tasksApi.assignTask(workspace?.workspaceId as string, taskId, assigneeUserId),
    onSuccess: invalidate,
  })
}

/**
 * Whether the signed-in person owns the task's project.
 *
 * One request, on the detail screen only, to answer the half of
 * `TaskAccessGuard.canChange` that the task response cannot: the project owner
 * may change any task in their project.
 *
 * The team-lead half of that rule is deliberately not answered. It would need
 * the workspace's teams as well, and a lead who is neither the assignee, the
 * reporter nor the project owner therefore sees the controls hidden even though
 * the API would allow them. That errs toward showing fewer controls than the
 * person has, and the backend remains the only thing that decides.
 */
export function useOwnsTaskProject(projectId: string | undefined): boolean {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const userId = useSessionStore((state) => state.user?.id ?? null)
  const { canReadProjects } = useTaskPermissions()

  const { data } = useQuery({
    queryKey: [...queryKeys.projects, workspaceId, 'ownership', projectId],
    queryFn: () => lookupsApi.getProjectOwnership(workspaceId as string, projectId as string),
    enabled: workspaceId !== null && projectId !== undefined && canReadProjects,
    staleTime: 5 * 60_000,
  })

  return userId !== null && data?.ownerUserId === userId
}
