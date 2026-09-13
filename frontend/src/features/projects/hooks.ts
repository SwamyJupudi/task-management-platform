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

import * as projectsApi from './api'
import * as lookupsApi from './lookups'
import type {
  CreateProjectInput,
  Project,
  ProjectFilters,
  ProjectMember,
  ProjectPageRequest,
  TeamOption,
  UpdateProjectInput,
  WorkspaceMemberOption,
} from './types'

/**
 * The project feature's server state, all of it through TanStack Query.
 *
 * Every key carries the workspace id. Two workspaces are two different answers
 * to the same question, and a key that left the workspace out would serve one
 * workspace's projects under the other's name for as long as the entry stayed
 * in the cache.
 *
 * Mutations invalidate rather than patching the cache by hand. The backend
 * derives `progress`, `memberCount` and `updatedAt`, and an optimistic edit
 * that guessed at those would show numbers the server never produced.
 */

const STALE_MS = 30_000

/** Everything under one workspace's projects, for a broad invalidation. */
function projectsRoot(workspaceId: string) {
  return [...queryKeys.projects, workspaceId] as const
}

// --- permissions ------------------------------------------------------------

/**
 * What the current user may do with projects in the active workspace.
 *
 * Every answer comes from a permission code, never from a role name: the same
 * catalog gates the API, and a role is only one of the ways a code is held.
 *
 * `canManage` is the interesting one, because the backend's rule is not a
 * single code. `ProjectAccessGuard.requireChangeableProject` wants the code
 * *and* either `project:manage_any` or that the caller owns the project or
 * leads its team. That last part needs the project in hand, so it is answered
 * per project by `useProjectAbilities` rather than here.
 *
 * None of this secures anything. It decides which controls are worth showing,
 * and every one of them is checked again server-side.
 */
export interface ProjectPermissions {
  canRead: boolean
  canCreate: boolean
  canUpdateAny: boolean
  canDeleteAny: boolean
  canManageMembersAny: boolean
  /** True when the caller may act on every project, not only their own. */
  manageAny: boolean
  canReadTeams: boolean
  canReadMembers: boolean
}

export function useProjectPermissions(): ProjectPermissions {
  const { has } = usePermissions()

  return {
    canRead: has('project:read'),
    canCreate: has('project:create'),
    canUpdateAny: has('project:update'),
    canDeleteAny: has('project:delete'),
    canManageMembersAny: has('project:manage_members'),
    manageAny: has('project:manage_any'),
    canReadTeams: has('team:read'),
    canReadMembers: has('member:read'),
  }
}

/**
 * Whether the caller may change one particular project.
 *
 * Mirrors the guard: holding the code is necessary, and on top of it either
 * `project:manage_any`, or owning the project, or leading the team that runs
 * it. The team lead half is why `teams` is a parameter — the teams list is
 * already loaded for the filters and the picker, so answering this needs no
 * request of its own.
 *
 * When the teams list is unavailable (no `team:read`) a lead who owns nothing
 * sees the controls hidden even though the API would allow them. Erring that
 * way shows fewer controls than the user has rather than more, and the backend
 * remains the only thing that decides.
 */
export function useProjectAbilities(
  project: Project | undefined,
  teams: readonly TeamOption[] | undefined,
) {
  const permissions = useProjectPermissions()
  const userId = useSessionStore((state) => state.user?.id ?? null)

  const owns =
    project !== undefined && project.ownerUserId !== null && project.ownerUserId === userId

  const teamId = project?.teamId ?? null
  const leadsTeam =
    teamId !== null &&
    teams?.some((team) => team.id === teamId && team.leadUserId === userId) === true

  const reaches = permissions.manageAny || owns || leadsTeam

  return {
    canEdit: permissions.canUpdateAny && reaches,
    canChangeStatus: permissions.canUpdateAny && reaches,
    canDelete: permissions.canDeleteAny && reaches,
    canManageMembers: permissions.canManageMembersAny && reaches,
  }
}

// --- queries ----------------------------------------------------------------

export function useProjects(
  filters: ProjectFilters,
  page: ProjectPageRequest,
): UseQueryResult<Page<Project>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useProjectPermissions()

  return useQuery({
    queryKey: [...projectsRoot(workspaceId ?? 'none'), 'list', filters, page],
    queryFn: () => projectsApi.listProjects(workspaceId as string, filters, page),
    enabled: workspaceId !== null && canRead,
    staleTime: STALE_MS,
    // Keeps the previous page on screen while the next one loads, so paging and
    // typing in the search box do not blank the table on every keystroke.
    placeholderData: (previous) => previous,
  })
}

export function useProject(projectId: string | undefined): UseQueryResult<Project> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useProjectPermissions()

  return useQuery({
    queryKey: [...projectsRoot(workspaceId ?? 'none'), 'detail', projectId],
    queryFn: () => projectsApi.getProject(workspaceId as string, projectId as string),
    enabled: workspaceId !== null && projectId !== undefined && canRead,
    staleTime: STALE_MS,
  })
}

export function useProjectMembers(
  projectId: string | undefined,
): UseQueryResult<Page<ProjectMember>> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canRead } = useProjectPermissions()

  return useQuery({
    queryKey: [...projectsRoot(workspaceId ?? 'none'), 'members', projectId],
    queryFn: () => projectsApi.listProjectMembers(workspaceId as string, projectId as string),
    enabled: workspaceId !== null && projectId !== undefined && canRead,
    staleTime: STALE_MS,
  })
}

/** The workspace's teams, for the team filter and the team picker. */
export function useTeamOptions(): UseQueryResult<TeamOption[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadTeams } = useProjectPermissions()

  return useQuery({
    queryKey: [...queryKeys.teams, workspaceId, 'options'],
    queryFn: () => lookupsApi.listTeams(workspaceId as string),
    enabled: workspaceId !== null && canReadTeams,
    // A team list changes far less often than a project list does.
    staleTime: 5 * 60_000,
  })
}

/** The workspace roster, for the owner filter and the member pickers. */
export function useMemberOptions(): UseQueryResult<WorkspaceMemberOption[]> {
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null
  const { canReadMembers } = useProjectPermissions()

  return useQuery({
    queryKey: [...queryKeys.users, workspaceId, 'options'],
    queryFn: () => lookupsApi.listWorkspaceMembers(workspaceId as string),
    enabled: workspaceId !== null && canReadMembers,
    staleTime: 5 * 60_000,
  })
}

// --- mutations --------------------------------------------------------------

/**
 * Invalidates every project query in the workspace.
 *
 * Broad on purpose. A project's status, priority or team decides which filtered
 * pages it belongs on, so a narrower invalidation would leave it listed under a
 * filter it no longer matches.
 */
function useInvalidateProjects() {
  const queryClient = useQueryClient()
  const workspace = useActiveWorkspace()
  const workspaceId = workspace?.workspaceId ?? null

  return () => {
    if (workspaceId === null) return
    void queryClient.invalidateQueries({ queryKey: projectsRoot(workspaceId) })
  }
}

export function useCreateProject(): UseMutationResult<Project, unknown, CreateProjectInput> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: (body: CreateProjectInput) =>
      projectsApi.createProject(workspace?.workspaceId as string, body),
    onSuccess: invalidate,
  })
}

export function useUpdateProject(
  projectId: string,
): UseMutationResult<Project, unknown, UpdateProjectInput> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: (body: UpdateProjectInput) =>
      projectsApi.updateProject(workspace?.workspaceId as string, projectId, body),
    onSuccess: invalidate,
  })
}

export function useChangeProjectStatus(): UseMutationResult<
  Project,
  unknown,
  { projectId: string; status: string }
> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: ({ projectId, status }) =>
      projectsApi.changeProjectStatus(workspace?.workspaceId as string, projectId, status),
    onSuccess: invalidate,
  })
}

export function useDeleteProject(): UseMutationResult<void, unknown, string> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: (projectId: string) =>
      projectsApi.deleteProject(workspace?.workspaceId as string, projectId),
    onSuccess: invalidate,
  })
}

export function useAddProjectMember(
  projectId: string,
): UseMutationResult<ProjectMember, unknown, string> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: (userId: string) =>
      projectsApi.addProjectMember(workspace?.workspaceId as string, projectId, userId),
    onSuccess: invalidate,
  })
}

export function useRemoveProjectMember(
  projectId: string,
): UseMutationResult<void, unknown, string> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: (userId: string) =>
      projectsApi.removeProjectMember(workspace?.workspaceId as string, projectId, userId),
    onSuccess: invalidate,
  })
}

export function useAssignProjectOwner(
  projectId: string,
): UseMutationResult<Project, unknown, string> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: (userId: string) =>
      projectsApi.assignProjectOwner(workspace?.workspaceId as string, projectId, userId),
    onSuccess: invalidate,
  })
}

export function useClearProjectOwner(projectId: string): UseMutationResult<Project, unknown, void> {
  const workspace = useActiveWorkspace()
  const invalidate = useInvalidateProjects()

  return useMutation({
    mutationFn: () => projectsApi.clearProjectOwner(workspace?.workspaceId as string, projectId),
    onSuccess: invalidate,
  })
}
